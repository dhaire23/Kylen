package com.takehaire.alpha.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Which image the still capture requests. */
enum class Pathway(val label: String) {
    RAW("RAW_SENSOR → HAIRE development"),
    YUV("YUV (processed, edge off, minimal NR) → HAIRE look"),
    JPEG("Vendor JPEG (fallback) → HAIRE look"),
}

sealed class CamState {
    data object Idle : CamState()
    data object Opening : CamState()
    data class Ready(val cameraId: String, val pathway: Pathway) : CamState()
    data class Error(val message: String, val recoverable: Boolean) : CamState()
}

data class SessionSpec(val cameraId: String, val pathway: Pathway, val wantDng: Boolean)

/** Everything the repeating preview request needs. Built by the view model from settings. */
data class PreviewControls(
    val manualExposure: Boolean = false,
    val exposureNs: Long = 8_000_000L,
    val iso: Int = 100,
    val aeCompSteps: Int = 0,
    val afMode: Int = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    val focusDistance: Float? = null,
    val afRegion: MeteringRectangle? = null,
    val awbMode: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
    val manualWbGains: RggbChannelVector? = null,
    val zoomRatio: Float = 1f,
    val faceDetect: Boolean = false,
)

/** Latest preview capture result, summarised (thread-safe snapshot). */
data class LiveResult(
    val exposureNs: Long = 0,
    val iso: Int = 0,
    val boost: Int = 100,
    val frameDurationNs: Long = 0,
    val afState: Int = CaptureResult.CONTROL_AF_STATE_INACTIVE,
    val aeState: Int = CaptureResult.CONTROL_AE_STATE_INACTIVE,
    val focusDistance: Float = 0f,
    val wbGains: RggbChannelVector? = null,
    val ccm: ColorSpaceTransform? = null,
    val tonemapMode: Int = -1,
    val activePhysicalId: String? = null,
    val faces: List<Rect> = emptyList(),
    val zoomRatio: Float = 1f,
    val cropRegion: Rect? = null,
) {
    /** Exposure product E = t × ISO × boost, in seconds·ISO. */
    val product: Double get() = exposureNs / 1e9 * iso * (boost / 100.0)
}

/** Still capture parameters (genuine sensor settings, AE disabled). */
data class StillSpec(
    val exposureNs: Long,
    val iso: Int,
    val controls: PreviewControls,
    val jpegOrientation: Int,
    val awbLockFromPreview: Boolean,
)

/** Copied-out capture data; all Images are already closed. */
class CaptureBundle(
    val result: TotalCaptureResult,
    val physicalResult: CaptureResult?,
    val rawData: ShortArray?,
    val rawWidth: Int,
    val rawHeight: Int,
    val rawRowStride: Int,
    val jpeg: ByteArray?,
    val yuvArgb: IntArray?,
    val yuvWidth: Int,
    val yuvHeight: Int,
    val pathway: Pathway,
    val cameraId: String,
    val timestampNs: Long,
)

/**
 * Camera2 hardware controller. All camera operations are serialised on one HandlerThread through a
 * mutex, so opening, closing, reconfiguring and capturing can never race each other.
 */
class CameraController(private val manager: CameraManager, private val caps: DeviceCaps) {
    private val thread = HandlerThread("haire-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val dispatcher = handler.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<CamState>(CamState.Idle)
    val state: StateFlow<CamState> = _state
    private val _live = MutableStateFlow(LiveResult())
    val live: StateFlow<LiveResult> = _live

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var spec: SessionSpec? = null
    private var previewTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size = Size(1440, 1080)
    private var rawReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var yuvReader: ImageReader? = null
    private var controls = PreviewControls()
    private var tonemapPreset = false
    var lastFrameDurationNs = 33_000_000L
        private set

    val currentSpec: SessionSpec? get() = spec
    val previewBufferSize: Size get() = previewSize

    fun setPreviewTexture(st: SurfaceTexture?) {
        previewTexture = st
    }

    fun info(): CameraInfo? = spec?.let { caps.info(it.cameraId) }

    // ------------------------------------------------------------------ open / configure
    suspend fun configure(newSpec: SessionSpec) = withContext(dispatcher) {
        mutex.withLock {
            val st = previewTexture ?: return@withLock
            if (spec == newSpec && session != null) return@withLock
            _state.value = CamState.Opening
            try {
                if (device?.id != newSpec.cameraId) {
                    closeLocked()
                    device = openDevice(newSpec.cameraId)
                } else {
                    closeSessionLocked()
                }
                val info = caps.info(newSpec.cameraId) ?: error("Unknown camera ${newSpec.cameraId}")
                var pathway = newSpec.pathway
                if (pathway == Pathway.RAW && !info.canDevelopRaw) pathway = Pathway.YUV
                if (pathway == Pathway.YUV && info.maxYuv == null) pathway = Pathway.JPEG
                previewSize = choosePreviewSize(info)
                st.setDefaultBufferSize(previewSize.width, previewSize.height)
                val surface = Surface(st)
                previewSurface?.release()
                previewSurface = surface
                var s = tryCreateSession(info, surface, pathway)
                if (s == null && pathway == Pathway.RAW) {
                    pathway = Pathway.YUV
                    s = tryCreateSession(info, surface, pathway)
                }
                if (s == null && pathway != Pathway.JPEG) {
                    pathway = Pathway.JPEG
                    s = tryCreateSession(info, surface, pathway)
                }
                s ?: error("No supported stream configuration")
                session = s
                spec = newSpec.copy(pathway = pathway)
                tonemapPreset = info.supportsSrgbTonemap && info.manualPostProcessing
                startRepeatingLocked()
                _state.value = CamState.Ready(newSpec.cameraId, pathway)
            } catch (e: CameraAccessException) {
                closeLocked()
                _state.value = CamState.Error(cameraErrorText(e.reason), recoverable = true)
            } catch (e: SecurityException) {
                closeLocked()
                _state.value = CamState.Error("Camera permission is needed to take photographs.", recoverable = false)
            } catch (e: Exception) {
                closeLocked()
                _state.value = CamState.Error(e.message ?: "The camera could not be opened.", recoverable = true)
            }
        }
    }

    private fun cameraErrorText(reason: Int) = when (reason) {
        CameraAccessException.CAMERA_IN_USE, CameraAccessException.MAX_CAMERAS_IN_USE -> "The camera is being used by another app. It will reconnect when it is free."
        CameraAccessException.CAMERA_DISABLED -> "The camera is disabled by a device policy."
        CameraAccessException.CAMERA_DISCONNECTED -> "The camera disconnected. Tap to retry."
        else -> "The camera reported an error. Tap to retry."
    }

    private fun choosePreviewSize(info: CameraInfo): Size {
        val aa = info.activeArray
        val aspect = if (aa != null) aa.width().toDouble() / aa.height() else 4.0 / 3
        val cands = info.privSizes.filter { kotlin.math.abs(it.width.toDouble() / it.height - aspect) < 0.02 && it.width <= 1920 }
        return cands.maxByOrNull { it.width * it.height } ?: Size(1440, 1080)
    }

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(id: String): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(id, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (cont.isActive) cont.resume(camera) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (cont.isActive) cont.resumeWithException(CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED))
                else onLostDevice(camera, "The camera was taken by another app or a call. It will reconnect.")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                val reason = when (error) {
                    ERROR_CAMERA_IN_USE -> CameraAccessException.CAMERA_IN_USE
                    ERROR_MAX_CAMERAS_IN_USE -> CameraAccessException.MAX_CAMERAS_IN_USE
                    ERROR_CAMERA_DISABLED -> CameraAccessException.CAMERA_DISABLED
                    else -> CameraAccessException.CAMERA_ERROR
                }
                if (cont.isActive) cont.resumeWithException(CameraAccessException(reason))
                else onLostDevice(camera, cameraErrorText(reason))
            }
        })
    }

    private fun onLostDevice(camera: CameraDevice, msg: String) {
        if (device === camera) {
            device = null
            session = null
            spec = null
            _state.value = CamState.Error(msg, recoverable = true)
        }
    }

    private suspend fun tryCreateSession(info: CameraInfo, preview: Surface, pathway: Pathway): CameraCaptureSession? {
        closeReaders()
        val outputs = ArrayList<OutputConfiguration>()
        outputs.add(OutputConfiguration(preview))
        when (pathway) {
            Pathway.RAW -> {
                val rs = info.maxRaw ?: return null
                rawReader = ImageReader.newInstance(rs.width, rs.height, ImageFormat.RAW_SENSOR, 2).also { outputs.add(OutputConfiguration(it.surface)) }
                info.maxJpeg?.let { js -> jpegReader = ImageReader.newInstance(js.width, js.height, ImageFormat.JPEG, 2).also { outputs.add(OutputConfiguration(it.surface)) } }
            }
            Pathway.YUV -> {
                val ys = info.maxYuv ?: return null
                yuvReader = ImageReader.newInstance(ys.width, ys.height, ImageFormat.YUV_420_888, 2).also { outputs.add(OutputConfiguration(it.surface)) }
            }
            Pathway.JPEG -> {
                val js = info.maxJpeg ?: return null
                jpegReader = ImageReader.newInstance(js.width, js.height, ImageFormat.JPEG, 2).also { outputs.add(OutputConfiguration(it.surface)) }
            }
        }
        val dev = device ?: return null
        val deferred = CompletableDeferred<CameraCaptureSession?>()
        val cfg = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) { deferred.complete(s) }
            override fun onConfigureFailed(s: CameraCaptureSession) { deferred.complete(null) }
        })
        if (Build.VERSION.SDK_INT >= 30) {
            val ok = try { dev.isSessionConfigurationSupported(cfg) } catch (_: Exception) { true }
            if (!ok) { closeReaders(); return null }
        }
        return try {
            dev.createCaptureSession(cfg)
            withTimeoutOrNull(5000) { deferred.await() }.also { if (it == null) closeReaders() }
        } catch (_: Exception) {
            closeReaders(); null
        }
    }

    // ------------------------------------------------------------------ repeating preview
    fun updateControls(c: PreviewControls) {
        scope.launchSafely {
            mutex.withLock {
                controls = c
                startRepeatingLocked()
            }
        }
    }

    private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
        launch {
            try { block() } catch (_: Exception) {}
        }
    }

    private fun applyCommon(b: CaptureRequest.Builder, info: CameraInfo, c: PreviewControls, still: Boolean) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        if (c.manualExposure && info.manualSensor) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, c.exposureNs)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, c.iso)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(c.exposureNs, 33_333_333L))
            if (info.postRawBoostRange != null) b.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 100)
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, c.aeCompSteps.coerceIn(info.aeCompRange.first, info.aeCompRange.last))
            info.fpsRanges.filter { it.upper == 30 }.minByOrNull { it.lower }?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }
        b.set(CaptureRequest.CONTROL_AF_MODE, if (c.afMode in info.afModes || c.afMode == CameraMetadata.CONTROL_AF_MODE_OFF) c.afMode else info.afModes.firstOrNull() ?: CameraMetadata.CONTROL_AF_MODE_OFF)
        if (c.afMode == CameraMetadata.CONTROL_AF_MODE_OFF && c.focusDistance != null) b.set(CaptureRequest.LENS_FOCUS_DISTANCE, c.focusDistance)
        c.afRegion?.let { b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it)) }
        val ccm = _live.value.ccm
        if (c.manualWbGains != null && info.manualPostProcessing && ccm != null) {
            b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            b.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            b.set(CaptureRequest.COLOR_CORRECTION_GAINS, c.manualWbGains)
            b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, ccm)
        } else {
            b.set(CaptureRequest.CONTROL_AWB_MODE, if (c.awbMode in info.awbModes) c.awbMode else CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        if (Build.VERSION.SDK_INT >= 30 && info.zoomRatioRange != null) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, c.zoomRatio.coerceIn(info.zoomRatioRange.start, info.zoomRatioRange.endInclusive))
        } else {
            info.activeArray?.let { aa ->
                val z = c.zoomRatio.coerceIn(1f, info.maxDigitalZoom)
                val w = (aa.width() / z).toInt(); val h = (aa.height() / z).toInt()
                b.set(CaptureRequest.SCALER_CROP_REGION, Rect(aa.centerX() - w / 2, aa.centerY() - h / 2, aa.centerX() + w / 2, aa.centerY() + h / 2))
            }
        }
        if (info.edgeOff) b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
        if (tonemapPreset) {
            b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_PRESET_CURVE)
            b.set(CaptureRequest.TONEMAP_PRESET_CURVE, CameraMetadata.TONEMAP_PRESET_CURVE_SRGB)
        }
        val nr = if (still) CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL else CameraMetadata.NOISE_REDUCTION_MODE_FAST
        when {
            nr in info.nrModes -> b.set(CaptureRequest.NOISE_REDUCTION_MODE, nr)
            CameraMetadata.NOISE_REDUCTION_MODE_FAST in info.nrModes -> b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
        }
        if (CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON in info.oisModes) b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        if (c.faceDetect && info.maxFaces > 0) {
            val mode = info.faceDetectModes.maxOrNull() ?: CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF
            b.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
        }
    }

    private fun startRepeatingLocked() {
        val s = session ?: return
        val dev = device ?: return
        val info = caps.info(dev.id) ?: return
        val surface = previewSurface ?: return
        try {
            val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(surface)
            applyCommon(b, info, controls, still = false)
            s.setRepeatingRequest(b.build(), previewCallback, handler)
        } catch (_: Exception) {
            // Session closed underneath us (backgrounding); the next configure() will rebuild it.
        }
    }

    private var lastPublishNs = 0L

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, r: TotalCaptureResult) {
            // Publish at ~10 Hz: enough for meters and focus confirmation without recomposing every frame.
            val now = System.nanoTime()
            val af = r.get(CaptureResult.CONTROL_AF_STATE)
            if (now - lastPublishNs < 100_000_000L && af == _live.value.afState) return
            lastPublishNs = now
            val faces = r.get(CaptureResult.STATISTICS_FACES)?.map { it.bounds } ?: emptyList()
            val phys = r.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            val zr = if (Build.VERSION.SDK_INT >= 30) r.get(CaptureResult.CONTROL_ZOOM_RATIO) ?: 1f else 1f
            lastFrameDurationNs = r.get(CaptureResult.SENSOR_FRAME_DURATION) ?: lastFrameDurationNs
            _live.value = LiveResult(
                exposureNs = r.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                iso = r.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                boost = r.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100,
                frameDurationNs = lastFrameDurationNs,
                afState = r.get(CaptureResult.CONTROL_AF_STATE) ?: CaptureResult.CONTROL_AF_STATE_INACTIVE,
                aeState = r.get(CaptureResult.CONTROL_AE_STATE) ?: CaptureResult.CONTROL_AE_STATE_INACTIVE,
                focusDistance = r.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f,
                wbGains = r.get(CaptureResult.COLOR_CORRECTION_GAINS),
                ccm = r.get(CaptureResult.COLOR_CORRECTION_TRANSFORM),
                tonemapMode = r.get(CaptureResult.TONEMAP_MODE) ?: -1,
                activePhysicalId = phys,
                faces = faces,
                zoomRatio = zr,
                cropRegion = r.get(CaptureResult.SCALER_CROP_REGION),
            )
        }
    }

    /** True when results confirm the sRGB preset tone curve is actually applied to the preview. */
    val tonemapHonored: Boolean get() = tonemapPreset && _live.value.tonemapMode == CameraMetadata.TONEMAP_MODE_PRESET_CURVE

    // ------------------------------------------------------------------ focus
    fun triggerFocus(region: MeteringRectangle?) {
        scope.launchSafely {
            mutex.withLock {
                val s = session ?: return@withLock
                val dev = device ?: return@withLock
                val info = caps.info(dev.id) ?: return@withLock
                if (CameraMetadata.CONTROL_AF_MODE_AUTO !in info.afModes) return@withLock
                controls = controls.copy(afMode = CameraMetadata.CONTROL_AF_MODE_AUTO, afRegion = region)
                val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(previewSurface ?: return@withLock)
                applyCommon(b, info, controls, still = false)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                s.capture(b.build(), null, handler)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                s.capture(b.build(), null, handler)
                startRepeatingLocked()
            }
        }
    }

    /** Waits (bounded) for AF to settle, for AF-S focus-priority capture. */
    private suspend fun awaitFocus(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val st = _live.value.afState
                if (st == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || st == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                    st == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || st == CaptureResult.CONTROL_AF_STATE_INACTIVE
                ) return@withTimeoutOrNull
                kotlinx.coroutines.delay(20)
            }
        }
    }

    // ------------------------------------------------------------------ still capture
    suspend fun capture(still: StillSpec, focusPriority: Boolean): CaptureBundle = withContext(dispatcher) {
        // AF-S is focus-priority (bounded wait); AF-C and MF release immediately.
        if (focusPriority && still.controls.afMode != CameraMetadata.CONTROL_AF_MODE_OFF) awaitFocus(900)
        mutex.withLock {
            val s = session ?: error("Camera not ready")
            val dev = device ?: error("Camera not ready")
            val sp = spec ?: error("Camera not ready")
            val info = caps.info(dev.id) ?: error("Camera not ready")
            drain(rawReader); drain(jpegReader); drain(yuvReader)
            val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            val manual = still.controls.copy(manualExposure = true, exposureNs = still.exposureNs, iso = still.iso)
            applyCommon(b, info, manual, still = true)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, still.exposureNs)
            if (still.awbLockFromPreview && still.controls.manualWbGains == null) b.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            b.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            b.set(CaptureRequest.JPEG_ORIENTATION, still.jpegOrientation)
            when (sp.pathway) {
                Pathway.RAW -> {
                    rawReader?.let { b.addTarget(it.surface) }
                    jpegReader?.let { b.addTarget(it.surface) }
                    b.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
                    if (CameraMetadata.SHADING_MODE_HIGH_QUALITY in (info.characteristics.get(android.hardware.camera2.CameraCharacteristics.SHADING_AVAILABLE_MODES)?.toList() ?: emptyList()))
                        b.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY)
                }
                Pathway.YUV -> yuvReader?.let { b.addTarget(it.surface) }
                Pathway.JPEG -> jpegReader?.let { b.addTarget(it.surface) }
            }
            val resultDeferred = CompletableDeferred<TotalCaptureResult>()
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    resultDeferred.complete(result)
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    resultDeferred.completeExceptionally(IllegalStateException("Capture failed (reason ${failure.reason})"))
                }
            }, handler)
            val timeout = still.exposureNs / 1_000_000 + 6000
            try {
                withTimeout(timeout) {
                    val result = resultDeferred.await()
                    val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L
                    val raw = rawReader?.let { awaitImage(it, ts) }
                    val jpg = jpegReader?.let { awaitImage(it, ts) }
                    val yuv = yuvReader?.let { awaitImage(it, ts) }
                    try {
                        var rawData: ShortArray? = null
                        var rw = 0; var rh = 0; var rs = 0
                        if (raw != null) {
                            val pl = raw.planes[0]
                            rw = raw.width; rh = raw.height
                            rs = pl.rowStride / 2
                            val sb = pl.buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            rawData = ShortArray(sb.remaining()).also { sb.get(it) }
                        }
                        val jpegBytes = jpg?.planes?.get(0)?.buffer?.let { bb -> ByteArray(bb.remaining()).also { bb.get(it) } }
                        val argb = yuv?.let { YuvConverter.toArgb(it) }
                        val physRes = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)?.let { result.physicalCameraResults[it] }
                        CaptureBundle(result, physRes, rawData, rw, rh, rs, jpegBytes, argb, yuv?.width ?: 0, yuv?.height ?: 0, sp.pathway, dev.id, ts)
                    } finally {
                        raw?.close(); jpg?.close(); yuv?.close()
                    }
                }
            } finally {
                startRepeatingLocked()
            }
        }
    }

    private fun drain(r: ImageReader?) {
        r ?: return
        while (true) { val img = try { r.acquireNextImage() } catch (_: Exception) { null } ?: break; img.close() }
    }

    private suspend fun awaitImage(reader: ImageReader, ts: Long): Image? {
        repeat(400) {
            val img = try { reader.acquireNextImage() } catch (_: IllegalStateException) { null }
            if (img != null) {
                if (ts == 0L || img.timestamp == ts) return img
                img.close()
            }
            kotlinx.coroutines.delay(10)
        }
        return null
    }

    // ------------------------------------------------------------------ teardown
    suspend fun close() = withContext(dispatcher) {
        mutex.withLock { closeLocked() }
        _state.value = CamState.Idle
    }

    private fun closeSessionLocked() {
        try { session?.stopRepeating() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        session = null
        closeReaders()
    }

    private fun closeReaders() {
        rawReader?.close(); rawReader = null
        jpegReader?.close(); jpegReader = null
        yuvReader?.close(); yuvReader = null
    }

    private fun closeLocked() {
        closeSessionLocked()
        try { device?.close() } catch (_: Exception) {}
        device = null
        spec = null
    }

    fun shutdown() {
        scope.launchSafely { mutex.withLock { closeLocked() } }
        thread.quitSafely()
    }
}

/** YUV_420_888 (JFIF full-range BT.601, as specified by Camera2) → packed ARGB, multithreaded. */
object YuvConverter {
    fun toArgb(img: Image): IntArray {
        val w = img.width
        val h = img.height
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        val yRow = yP.rowStride; val yPix = yP.pixelStride
        val uRow = uP.rowStride; val uPix = uP.pixelStride
        val vRow = vP.rowStride; val vPix = vP.pixelStride
        val yA = ByteArray(yB.remaining()).also { yB.get(it) }
        val uA = ByteArray(uB.remaining()).also { uB.get(it) }
        val vA = ByteArray(vB.remaining()).also { vB.get(it) }
        val out = IntArray(w * h)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val ts = (0 until threads).map { t ->
            Thread {
                var y = t
                while (y < h) {
                    for (x in 0 until w) {
                        val yy = (yA[y * yRow + x * yPix].toInt() and 0xFF).toFloat()
                        val cx = x shr 1; val cy = y shr 1
                        val u = (uA[cy * uRow + cx * uPix].toInt() and 0xFF) - 128f
                        val v = (vA[cy * vRow + cx * vPix].toInt() and 0xFF) - 128f
                        val r = (yy + 1.402f * v).toInt().coerceIn(0, 255)
                        val g = (yy - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                        val b = (yy + 1.772f * u).toInt().coerceIn(0, 255)
                        out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    y += threads
                }
            }.also { it.start() }
        }
        ts.forEach { it.join() }
        return out
    }
}
