package com.takehaire.alpha

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.takehaire.alpha.camera.CamState
import com.takehaire.alpha.camera.CameraController
import com.takehaire.alpha.camera.CaptureContext
import com.takehaire.alpha.camera.CaptureProcessor
import com.takehaire.alpha.camera.DeviceCaps
import com.takehaire.alpha.camera.LiveResult
import com.takehaire.alpha.camera.LutCache
import com.takehaire.alpha.camera.Pathway
import com.takehaire.alpha.camera.PhotoStore
import com.takehaire.alpha.camera.PreviewControls
import com.takehaire.alpha.camera.ProcessedPhoto
import com.takehaire.alpha.camera.SessionSpec
import com.takehaire.alpha.camera.StillSpec
import com.takehaire.alpha.gl.FrameStats
import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.exposure.AutoIsoSettings
import com.takehaire.alpha.imaging.exposure.ExposureDecision
import com.takehaire.alpha.imaging.exposure.ExposureProgram
import com.takehaire.alpha.imaging.exposure.ExposureWarning
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.exposure.Stops
import com.takehaire.alpha.imaging.lens.CropClass
import com.takehaire.alpha.imaging.lens.LensKind
import com.takehaire.alpha.imaging.lens.LensMath
import com.takehaire.alpha.imaging.lens.LensPlan
import com.takehaire.alpha.imaging.lens.LensProfile
import com.takehaire.alpha.imaging.look.LookSpec
import com.takehaire.alpha.imaging.look.Recipe
import com.takehaire.alpha.imaging.look.withRecipe
import com.takehaire.alpha.imaging.lut.CubeParser
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.settings.AppSettings
import com.takehaire.alpha.settings.Defaults
import com.takehaire.alpha.settings.FocusArea
import com.takehaire.alpha.settings.FocusMode
import com.takehaire.alpha.settings.ImageFormat
import com.takehaire.alpha.settings.PathwayPreference
import com.takehaire.alpha.settings.SettingsRepository
import com.takehaire.alpha.settings.ShootingPreset
import com.takehaire.alpha.settings.VolumeKeyAction
import com.takehaire.alpha.settings.WbMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** A short, actionable message for the photographer (never a stack trace). */
data class Notice(val text: String, val id: Long = System.nanoTime())

data class LastCapture(
    val uri: Uri?,
    val thumb: Bitmap?,
    val processed: ProcessedPhoto?,
    val requestedExposureNs: Long,
    val requestedIso: Int,
)

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val store = PhotoStore(app)
    private val luts = LutCache()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _caps = MutableStateFlow<DeviceCaps?>(null)
    val caps: StateFlow<DeviceCaps?> = _caps.asStateFlow()

    var controller: CameraController? = null
        private set
    private var processor: CaptureProcessor? = null

    private val _stats = MutableStateFlow<FrameStats?>(null)
    val stats: StateFlow<FrameStats?> = _stats.asStateFlow()

    private val _last = MutableStateFlow<LastCapture?>(null)
    val last: StateFlow<LastCapture?> = _last.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** Incremented when auto review should open playback after a capture. */
    private val _reviewRequest = MutableStateFlow(0)
    val reviewRequest: StateFlow<Int> = _reviewRequest.asStateFlow()

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _previewLut = MutableStateFlow<Lut3D?>(null)
    val previewLut: StateFlow<Lut3D?> = _previewLut.asStateFlow()

    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val lookThumbs: StateFlow<Map<String, Bitmap>> = _thumbs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 0/90/180/270: physical device orientation (for photo orientation and UI glyph rotation). */
    @Volatile var deviceOrientation = 0
    @Volatile var displayRotation = 0
    private var cubeLut: Lut3D? = null
    private val processing = Semaphore(1)
    private var lutJob: Job? = null
    private var saveJob: Job? = null
    private val sound = MediaActionSound().apply { try { load(MediaActionSound.SHUTTER_CLICK) } catch (_: Exception) {} }

    init {
        viewModelScope.launch {
            val s = repo.load()
            _settings.value = s
            if (repo.lastLoadWasCorrupt) notify("Settings could not be read and were reset to factory defaults.")
            _caps.value = withContext(Dispatchers.IO) { DeviceCaps.discover(manager) }
            _caps.value?.let { c ->
                controller = CameraController(manager, c).also { ctl -> pendingTexture?.let { ctl.setPreviewTexture(it) } }
                processor = CaptureProcessor(app, c, store, luts)
                loadCubeLut()
            }
            if (_caps.value?.cameras.isNullOrEmpty()) notify("No camera is available on this device.")
            adaptFactoryLensToHardware()
            _loaded.value = true
            refreshPreviewLut()
            reconfigure()
            withContext(Dispatchers.IO) { store.list(1).firstOrNull() }?.let { _last.value = LastCapture(it.uri, null, null, 0, 0) }
        }
    }

    fun notify(text: String) { _notice.value = Notice(text) }
    fun clearNotice() { _notice.value = null }

    // ------------------------------------------------------------------ settings helpers
    val current: ShootingPreset get() = _settings.value.current

    fun update(transform: (AppSettings) -> AppSettings) {
        val before = _settings.value
        val after = SettingsRepository.sanitize(transform(before))
        if (after == before) return
        _settings.value = after
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); repo.save(_settings.value) }
        val b = before.current
        val a = after.current
        if (b.lookId != a.lookId || b.lookIntensity != a.lookIntensity || before.wbShiftAB != after.wbShiftAB ||
            before.wbShiftGM != after.wbShiftGM || before.recipes != after.recipes || before.settingEffect != after.settingEffect
        ) refreshPreviewLut()
        if (before.frontCamera != after.frontCamera || b.format != a.format || before.pathway != after.pathway ||
            b.processing != a.processing || b.lensId != a.lensId || b.zoomFocal != a.zoomFocal || b.aspect != a.aspect
        ) reconfigure()
        pushControls()
    }

    fun updatePreset(t: (ShootingPreset) -> ShootingPreset) = update { it.copy(current = t(it.current)) }

    // ------------------------------------------------------------------ looks
    fun lookFor(id: String): LookSpec {
        val s = _settings.value
        val recipe = s.recipes.firstOrNull { it.id == id }
        return if (recipe != null) Assets.look(recipe.baseLookId).withRecipe(recipe) else Assets.look(id)
    }

    val activeLook: LookSpec get() = lookFor(current.lookId)

    private fun loadCubeLut() {
        val r = _settings.value.recipes.firstOrNull { it.id == current.lookId }
        cubeLut = r?.cubeLutFile?.let { f ->
            try { CubeParser.parse(File(getApplication<Application>().filesDir, f).readText()) } catch (_: Exception) { null }
        }
    }

    private fun refreshPreviewLut() {
        lutJob?.cancel()
        lutJob = viewModelScope.launch(Dispatchers.Default) {
            delay(40)
            loadCubeLut()
            val s = _settings.value
            val look = activeLook.let { it.copy(warmth = it.warmth + s.wbShiftAB, tint = it.tint + s.wbShiftGM) }
            val honored = controller?.tonemapHonored ?: true
            _previewLut.value = if (!s.settingEffect) Lut3D.identity(2) else luts.preview(look, current.lookIntensity, honored, cubeLut)
        }
    }

    fun buildLookThumbnails(px: IntArray, w: Int, h: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val honored = controller?.tonemapHonored ?: true
            val ids = Assets.looks.looks.map { it.id } + _settings.value.recipes.map { it.id }
            val o = FloatArray(3)
            val out = HashMap<String, Bitmap>()
            for (id in ids) {
                val lut = luts.thumb(lookFor(id), honored)
                val dst = IntArray(px.size)
                for (i in px.indices) {
                    val p = px[i]
                    lut.lookup(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f, o)
                    dst[i] = (0xFF shl 24) or ((o[0] * 255).roundToInt().coerceIn(0, 255) shl 16) or
                        ((o[1] * 255).roundToInt().coerceIn(0, 255) shl 8) or (o[2] * 255).roundToInt().coerceIn(0, 255)
                }
                out[id] = Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
                _thumbs.value = HashMap(out)
            }
        }
    }

    // ------------------------------------------------------------------ lenses
    val lenses: List<LensProfile> get() = Assets.lenses.lenses

    fun lens(id: String = current.lensId): LensProfile = lenses.firstOrNull { it.id == id } ?: lenses.first { it.id == "p40_25" }

    fun focalFor(l: LensProfile, p: ShootingPreset = current): Double =
        if (l.kind == LensKind.PRIME) l.minFocal else (p.zoomFocal ?: l.minFocal).coerceIn(l.minFocal, l.maxFocal)

    fun plan(l: LensProfile = lens(), p: ShootingPreset = current): LensPlan? {
        val c = _caps.value ?: return null
        if (_settings.value.frontCamera) return null
        return LensMath.plan(focalFor(l, p), p.aspect, c.opticalSources(p.aspect))
    }

    /** Lens availability for the camera bag (never presents an unusable lens as normal). */
    fun lensStatus(l: LensProfile): CropClass? {
        val c = _caps.value ?: return null
        val src = c.opticalSources(current.aspect)
        val a = LensMath.plan(l.minFocal, current.aspect, src)
        val b = LensMath.plan(l.maxFocal, current.aspect, src)
        return listOfNotNull(a?.cropClass, b?.cropClass).minByOrNull { it.ordinal } ?: CropClass.UNAVAILABLE
    }

    /**
     * Spec §44: the factory 40mm is kept only where it does not need substantial cropping; otherwise
     * the closest high-quality focal length is mounted instead (once, on first launch).
     */
    private fun adaptFactoryLensToHardware() {
        val s = _settings.value
        if (s.hardwareAdapted || _caps.value?.back.isNullOrEmpty()) return
        var lensId = s.current.lensId
        val good = setOf(CropClass.NATIVE, CropClass.MODERATE)
        if (lensId == Defaults.factory.lensId && lensStatus(lens(lensId)) !in good) {
            lensId = listOf("p35_14", "p28_2", "p24_14").firstOrNull { lensStatus(lens(it)) in good } ?: lensId
        }
        update { it.copy(current = it.current.copy(lensId = lensId), hardwareAdapted = true) }
    }

    fun mountLens(id: String) = updatePreset { p ->
        val l = lens(id)
        p.copy(lensId = id, zoomFocal = if (l.kind == LensKind.ZOOM) l.minFocal else null,
            virtualAperture = p.virtualAperture.coerceIn(l.maxAperture, l.minAperture))
    }

    fun setZoomFocal(f: Double) = updatePreset { p ->
        val l = lens(p.lensId)
        if (l.kind != LensKind.ZOOM) p else p.copy(zoomFocal = f.coerceIn(l.minFocal, l.maxFocal))
    }

    // ------------------------------------------------------------------ camera session
    private fun cameraIdAndZoom(): Pair<String, Float>? {
        val c = _caps.value ?: return null
        if (_settings.value.frontCamera) return (c.front ?: c.primaryBack ?: return null).id to 1f
        val p = plan()
        if (p != null && p.cropClass != CropClass.UNAVAILABLE) return p.source.cameraId to p.zoomRatio.toFloat()
        val primary = c.primaryBack ?: c.cameras.firstOrNull() ?: return null
        return primary.id to 1f
    }

    private fun desiredPathway(cameraId: String): Pathway {
        val info = _caps.value?.info(cameraId)
        val s = _settings.value
        val wantRaw = when (s.pathway) {
            PathwayPreference.RAW -> true
            PathwayPreference.PROCESSED -> false
            PathwayPreference.AUTO -> com.takehaire.alpha.imaging.calib.ProcessingModes.profile(current.processing).preferRaw
        } || current.format != ImageFormat.JPEG
        return if (wantRaw && info?.canDevelopRaw == true) Pathway.RAW else Pathway.YUV
    }

    private var reconfigureJob: Job? = null
    fun reconfigure() {
        val ctl = controller ?: return
        val (id, _) = cameraIdAndZoom() ?: return
        reconfigureJob?.cancel()
        reconfigureJob = viewModelScope.launch {
            ctl.configure(SessionSpec(id, desiredPathway(id), current.format != ImageFormat.JPEG))
            pushControls()
            refreshPreviewLut()
            // Whether the sRGB preset tone curve is honoured is only known from results: re-bake then.
            delay(700)
            refreshPreviewLut()
        }
    }

    private var pendingTexture: android.graphics.SurfaceTexture? = null

    /** The GL surface can be ready before camera discovery finishes; the texture is kept until then. */
    fun onPreviewSurfaceReady(st: android.graphics.SurfaceTexture) {
        pendingTexture = st
        controller?.setPreviewTexture(st)
        reconfigure()
    }

    fun onResumeCamera() { reconfigure() }

    fun onPauseCamera() {
        viewModelScope.launch { controller?.close() }
    }

    fun onStats(s: FrameStats) { _stats.value = s }

    fun onGpuError(msg: String) = notify("The viewfinder's colour preview could not start ($msg). Photos are unaffected.")

    // ------------------------------------------------------------------ exposure
    fun liveResult(): LiveResult = controller?.live?.value ?: LiveResult()

    private fun cameraInfo() = controller?.currentSpec?.let { _caps.value?.info(it.cameraId) }

    /** Metered exposure product (t·ISO) the camera considers correct, including exposure comp. */
    fun meteredProduct(): Double? {
        val live = liveResult()
        if (live.exposureNs <= 0 || live.iso <= 0) return null
        val p = current
        return if (p.mode == ShootingMode.M && _settings.value.settingEffect) {
            // AE is off in M: meter from the preview luminance instead.
            val l = _stats.value?.meterLinear ?: return null
            if (l <= 1e-4f) null else live.product * 0.18 / l * 2.0.pow(p.exposureComp)
        } else live.product
    }

    fun decision(): ExposureDecision? {
        val info = cameraInfo() ?: return null
        val e = meteredProduct() ?: return null
        val p = current
        val s = _settings.value
        return ExposureProgram.decide(
            p.mode, e, info.exposureLimits(), AutoIsoSettings(p.isoMin, p.isoMax, p.minShutterS),
            userShutterS = p.shutterS, userIso = p.iso, manualAutoIso = s.manualAutoIso, isoAuto = p.isoAuto,
        )
    }

    fun shutterValues(): List<Double> {
        val info = cameraInfo() ?: return Stops.SHUTTER
        val l = info.exposureLimits()
        return Stops.shutterRange(l.minExposureS, l.maxExposureS)
    }

    fun isoValues(): List<Int> {
        val info = cameraInfo() ?: return Stops.ISO
        val l = info.exposureLimits()
        return Stops.isoRange(l.minIso, l.maxIso)
    }

    fun apertureValues(): List<Double> {
        val l = lens()
        return Stops.APERTURE.filter { it >= l.maxAperture - 1e-6 && it <= l.minAperture + 1e-6 }
    }

    fun stepShutter(d: Int) = updatePreset { p ->
        val v = shutterValues(); val i = Stops.nearestIndex(v, p.shutterS)
        p.copy(shutterS = v[(i + d).coerceIn(0, v.size - 1)])
    }

    fun stepIso(d: Int) = updatePreset { p ->
        val v = isoValues()
        if (p.isoAuto && d < 0) return@updatePreset p
        if (p.isoAuto) return@updatePreset p.copy(isoAuto = false, iso = decision()?.iso?.let { v[Stops.nearestIsoIndex(v, it)] } ?: p.iso)
        val i = Stops.nearestIsoIndex(v, p.iso)
        if (i + d < 0 && p.mode != ShootingMode.M) p.copy(isoAuto = true)
        else p.copy(iso = v[(i + d).coerceIn(0, v.size - 1)])
    }

    fun setIsoAuto(auto: Boolean) = updatePreset { it.copy(isoAuto = auto) }

    fun setBokeh(on: Boolean) {
        updatePreset { it.copy(bokeh = on) }
        if (on) {
            viewModelScope.launch(Dispatchers.IO) { processor?.prefetchBokehModel() }
            notify("Virtual bokeh on (experimental): the background blur is added when the photo is saved, not in the viewfinder. Lower F numbers blur more.")
        }
    }

    fun stepAperture(d: Int) = updatePreset { p ->
        val v = apertureValues(); if (v.isEmpty()) return@updatePreset p
        val i = Stops.nearestIndex(v, p.virtualAperture)
        p.copy(virtualAperture = v[(i + d).coerceIn(0, v.size - 1)])
    }

    fun stepExposureComp(d: Int) = updatePreset { p ->
        val info = cameraInfo()
        val step = 1.0 / 3
        val max = info?.let { it.aeCompRange.last * it.aeCompStep.toDouble() }?.coerceAtMost(5.0) ?: 3.0
        val min = info?.let { it.aeCompRange.first * it.aeCompStep.toDouble() }?.coerceAtLeast(-5.0) ?: -3.0
        p.copy(exposureComp = ((p.exposureComp + d * step) * 3).roundToInt() / 3.0).let { it.copy(exposureComp = it.exposureComp.coerceIn(min, max)) }
    }

    fun setMode(m: ShootingMode) = update { s ->
        // Switching modes keeps the photographer's values; seeding S/M from the current exposure avoids jumps.
        val d = decision()
        var p = s.current.copy(mode = m)
        if (d != null && (m == ShootingMode.S || m == ShootingMode.M) && s.current.mode != ShootingMode.S && s.current.mode != ShootingMode.M) {
            val sh = shutterValues()
            p = p.copy(shutterS = sh[Stops.nearestIndex(sh, d.exposureS)])
            if (m == ShootingMode.M) { val iv = isoValues(); p = p.copy(iso = iv[Stops.nearestIsoIndex(iv, d.iso)], isoAuto = false) }
        }
        s.copy(current = p, memorySlot = null)
    }

    fun recallMemory(slot: Int) = update { s ->
        val m = s.memories.getOrNull(slot) ?: return@update s
        s.copy(current = m.copy(name = s.current.name), memorySlot = slot)
    }

    fun saveMemory(slot: Int) = update { s ->
        val list = s.memories.toMutableList()
        list[slot] = s.current.copy(name = Defaults.memories[slot].name)
        notify("Saved to ${Defaults.memories[slot].name}")
        s.copy(memories = list)
    }

    fun resetToFactory() {
        update { AppSettings(learnedBaseline = it.learnedBaseline) }
        adaptFactoryLensToHardware()
        notify("Factory settings restored.")
    }

    // ------------------------------------------------------------------ preview controls
    fun previewControls(): PreviewControls {
        val p = current
        val s = _settings.value
        val info = cameraInfo()
        val zoom = cameraIdAndZoom()?.second ?: 1f
        val afMode = when (p.focusMode) {
            FocusMode.MF -> CameraMetadata.CONTROL_AF_MODE_OFF
            FocusMode.AF_C -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            FocusMode.AF_S -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }
        val awb = when (p.wbMode) {
            WbMode.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            WbMode.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            WbMode.CLOUDY -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            WbMode.SHADE -> CameraMetadata.CONTROL_AWB_MODE_SHADE
            WbMode.TUNGSTEN -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            WbMode.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            WbMode.KELVIN, WbMode.CUSTOM -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        }
        val manualM = p.mode == ShootingMode.M && s.settingEffect && info?.manualSensor == true
        var expNs = (p.shutterS * 1e9).toLong()
        var iso = p.iso
        if (manualM && s.manualAutoIso) decision()?.let { iso = it.iso }
        if (manualM && expNs > 66_000_000L) {
            // Long exposure: preview at 1/15 s with equivalent gain (shown as an approximation).
            val factor = expNs / 66_000_000.0
            expNs = 66_000_000L
            iso = (iso * factor).roundToInt().coerceAtMost(info?.isoRange?.last ?: iso)
        }
        val evSteps = info?.let { (p.exposureComp / it.aeCompStep).roundToInt() } ?: 0
        return PreviewControls(
            manualExposure = manualM,
            exposureNs = expNs,
            iso = iso,
            aeCompSteps = evSteps,
            afMode = afMode,
            focusDistance = if (p.focusMode == FocusMode.MF) manualFocus else null,
            afRegion = touchRegion ?: areaRegion(s.focusArea),
            awbMode = awb,
            manualWbGains = manualGains(),
            zoomRatio = zoom,
            faceDetect = s.focusArea == FocusArea.FACE,
        )
    }

    /** Effective WB Kelvin when fixed (manual Kelvin, or a tungsten film stock under AWB). */
    fun fixedKelvin(): Pair<Int, Int>? {
        val p = current
        if (p.wbMode == WbMode.KELVIN || p.wbMode == WbMode.CUSTOM) return p.kelvin to p.tint
        if (p.wbMode == WbMode.AUTO) activeLook.filmWhiteBalanceK?.let { return it.roundToInt() to 0 }
        return null
    }

    private fun manualGains(): RggbChannelVector? {
        val (k, t) = fixedKelvin() ?: return null
        val info = cameraInfo() ?: return null
        val model = info.colorModel ?: return null
        if (!info.manualPostProcessing) return null
        val n = model.neutralForWhiteBalance(k.toDouble(), t.toDouble())
        val g = (1.0 / n.y).toFloat()
        return RggbChannelVector((1.0 / n.x).toFloat() / g, 1f, 1f, (1.0 / n.z).toFloat() / g)
    }

    /** Centre / flexible-spot focus areas as AF regions on the active array (Wide = camera's own). */
    private fun areaRegion(area: FocusArea): MeteringRectangle? {
        val aa = cameraInfo()?.activeArray ?: return null
        val frac = when (area) { FocusArea.CENTER -> 0.16f; FocusArea.SPOT -> 0.08f; else -> return null }
        val hw = (aa.width() * frac / 2).toInt()
        val hh = (aa.height() * frac / 2).toInt()
        return MeteringRectangle(Rect(aa.centerX() - hw, aa.centerY() - hh, aa.centerX() + hw, aa.centerY() + hh), MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    @Volatile var manualFocus: Float = 0f
    @Volatile var magnifying = false
    @Volatile var thumbnailRequest: ((IntArray, Int, Int) -> Unit)? = null

    fun requestLookThumbnails() {
        thumbnailRequest = { px, w, h -> buildLookThumbnails(px, w, h) }
    }
    private var touchRegion: MeteringRectangle? = null

    fun pushControls() {
        controller?.updateControls(previewControls())
    }

    fun applyManualFocus(d: Float) {
        manualFocus = d
        pushControls()
    }

    /** Touch focus at normalised viewfinder coordinates; never changes exposure. */
    fun focusAt(u: Float, v: Float, viewAspect: Float) {
        val info = cameraInfo() ?: return
        val aa = liveResult().cropRegion ?: info.activeArray ?: return
        // View → natural-orientation content (undo centre crop and display rotation).
        val natW = 3f; val natH = 4f
        val rot = displayRotation
        val (cw, ch) = if (rot == 90 || rot == 270) natH to natW else natW to natH
        var x = u; var y = v
        val ca = cw / ch
        if (ca > viewAspect) x = 0.5f + (x - 0.5f) * viewAspect / ca else y = 0.5f + (y - 0.5f) * ca / viewAspect
        repeat((rot / 90) % 4) { val nx = 1f - y; y = x; x = nx }
        // Natural portrait → sensor (landscape) coordinates using the sensor orientation.
        val (sx, sy) = when (info.sensorOrientation) {
            90 -> y to 1f - x
            270 -> 1f - y to x
            180 -> 1f - x to 1f - y
            else -> x to y
        }
        val half = (aa.width() * 0.06f).toInt()
        val cx = (aa.left + sx * aa.width()).toInt()
        val cy = (aa.top + sy * aa.height()).toInt()
        val r = Rect((cx - half).coerceAtLeast(aa.left), (cy - half).coerceAtLeast(aa.top), (cx + half).coerceAtMost(aa.right), (cy + half).coerceAtMost(aa.bottom))
        touchRegion = MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1)
        if (current.focusMode != FocusMode.MF) controller?.triggerFocus(touchRegion)
    }

    fun clearTouchFocus() {
        touchRegion = null
        pushControls()
    }

    // ------------------------------------------------------------------ capture
    fun jpegOrientation(): Int {
        val info = cameraInfo() ?: return 0
        val dev = (deviceOrientation + 45) / 90 * 90 % 360
        return if (info.facing == CameraCharacteristics.LENS_FACING_FRONT) (info.sensorOrientation - dev + 360) % 360
        else (info.sensorOrientation + dev) % 360
    }

    fun shutter() {
        val ctl = controller ?: return
        if (ctl.state.value !is CamState.Ready) { notify("The camera is not ready yet."); return }
        if (_pending.value >= 2) { notify("Still saving the previous photos…"); return }
        if (!store.hasSpaceForCapture()) { notify("Storage is almost full. Free some space to keep shooting."); return }
        val d = decision()
        val info = cameraInfo() ?: return
        val p = current
        val s = _settings.value
        val lim = info.exposureLimits()
        val expNs = ((d?.exposureS ?: p.shutterS).coerceIn(lim.minExposureS, lim.maxExposureS) * 1e9).toLong()
        val iso = (d?.iso ?: p.iso).coerceIn(lim.minIso, lim.maxIso)
        val rotation = jpegOrientation()
        val meterSnapshot = _stats.value?.meterLinear
        val productSnapshot = liveResult().product
        val plan = plan()
        val l = lens()
        val ctx = CaptureContext(
            look = activeLook, intensity = p.lookIntensity, processing = p.processing, format = p.format, aspect = p.aspect,
            rawDigitalCrop = plan?.digitalCrop ?: 1.0, rotation = rotation, mode = p.mode, exposureComp = p.exposureComp,
            manualWb = fixedKelvin(), wbShift = s.wbShiftAB to s.wbShiftGM,
            profile = Assets.sensorProfile(android.os.Build.MODEL ?: "", info.id),
            learnedBaselineEv = s.learnedBaseline[info.id], previewMeter = meterSnapshot,
            previewProduct = productSnapshot, tonemapHonored = ctl.tonemapHonored,
            lensName = if (s.frontCamera) "Front camera" else l.name,
            equivalentFocal = plan?.targetEquivalent ?: (info.equivalentFocal(p.aspect) ?: 0.0),
            virtualAperture = if (p.bokeh) p.virtualAperture else null, cubeLut = cubeLut, cameraIdForBokeh = info.id,
        )
        playShutterSound()
        _pending.update { it + 1 }
        viewModelScope.launch {
            try {
                val bundle = ctl.capture(
                    StillSpec(expNs, iso, previewControls(), rotation, awbLockFromPreview = true),
                    focusPriority = p.focusMode == FocusMode.AF_S,
                )
                val result = withContext(Dispatchers.Default) {
                    processing.withPermit { processor!!.process(bundle, ctx) }
                }
                result.anchorEv?.let { ev -> learnBaseline(info.id, ev) }
                _last.value = LastCapture(result.uris.firstOrNull { it.toString().isNotEmpty() }, result.thumbnail, result, expNs, iso)
                result.note?.let { notify(it) }
                if (_settings.value.autoReview && result.uris.isNotEmpty()) _reviewRequest.update { it + 1 }
            } catch (e: OutOfMemoryError) {
                notify("Not enough memory to process this photo. Try closing other apps.")
            } catch (e: Exception) {
                notify("The photo could not be taken: ${e.message ?: "camera error"}. Please try again.")
            } finally {
                _pending.update { it - 1 }
            }
        }
    }

    private fun learnBaseline(cameraId: String, ev: Double) = update { s ->
        val old = s.learnedBaseline[cameraId]
        val v = if (old == null) ev else old * 0.8 + ev * 0.2
        s.copy(learnedBaseline = s.learnedBaseline + (cameraId to v))
    }

    private fun playShutterSound() {
        try {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mandatory = if (android.os.Build.VERSION.SDK_INT >= 33) MediaActionSound.mustPlayShutterSound() else false
            if (mandatory || am.ringerMode == AudioManager.RINGER_MODE_NORMAL) sound.play(MediaActionSound.SHUTTER_CLICK)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ volume keys
    fun onVolumeKey(up: Boolean): Boolean = when (_settings.value.volumeKey) {
        VolumeKeyAction.SHUTTER -> { shutter(); true }
        VolumeKeyAction.EXPOSURE -> { stepExposureComp(if (up) 1 else -1); true }
        VolumeKeyAction.ZOOM -> {
            val l = lens()
            if (l.kind == LensKind.ZOOM) setZoomFocal(focalFor(l) * if (up) 1.06 else 1 / 1.06)
            true
        }
        VolumeKeyAction.OFF -> false
    }

    // ------------------------------------------------------------------ recipes / LUT import
    fun saveRecipe(r: Recipe) = update { s ->
        val list = s.recipes.filter { it.id != r.id } + r
        s.copy(recipes = list)
    }

    fun deleteRecipe(id: String) = update { s ->
        s.copy(recipes = s.recipes.filter { it.id != id }, current = if (s.current.lookId == id) s.current.copy(lookId = "alpha_natural") else s.current)
    }

    /** Imports and validates a .cube file; returns an error message or null on success. */
    fun importCube(uri: Uri, baseLookId: String): String? {
        return try {
            val ctx = getApplication<Application>()
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return "The file could not be opened."
            if (text.length > 20_000_000) return "The LUT file is too large."
            val lut = CubeParser.parse(text)
            val id = "cube_" + System.currentTimeMillis()
            File(ctx.filesDir, "$id.cube").writeText(text)
            saveRecipe(Recipe(id, lut.title.ifBlank { "Imported LUT" }.take(24).uppercase(), baseLookId, cubeLutFile = "$id.cube"))
            null
        } catch (e: Exception) {
            "This .cube file is not supported: ${e.message}"
        }
    }

    fun exportRecipes(): String = repo.json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), _settings.value.recipes,
    )

    fun importRecipes(text: String): String? = try {
        val list = repo.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), text)
        list.forEach { saveRecipe(it) }
        null
    } catch (e: Exception) {
        "Recipes could not be imported: ${e.message}"
    }

    fun isPowerSaving(): Boolean = (getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    fun thermalThrottled(): Boolean = try {
        (getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    } catch (_: Exception) {
        false
    }

    override fun onCleared() {
        controller?.shutdown()
        try { sound.release() } catch (_: Exception) {}
        super.onCleared()
    }

    companion object {
        fun formatFocal(f: Double) = "${f.roundToInt()}mm"
        fun approxEquals(a: Double, b: Double) = abs(ln(a / b)) < 0.05
    }
}
