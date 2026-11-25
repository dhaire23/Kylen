package com.takehaire.alpha.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.ColorSpaceTransform
import android.os.Build
import android.util.Size
import android.util.SizeF
import com.takehaire.alpha.imaging.color.DngColorModel
import com.takehaire.alpha.imaging.color.Mat3
import com.takehaire.alpha.imaging.exposure.ExposureLimits
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.lens.LensMath
import com.takehaire.alpha.imaging.lens.OpticalSource

/** Everything we learned about one camera id (logical or physical) from CameraCharacteristics. */
data class CameraInfo(
    val id: String,
    val facing: Int,
    val isLogical: Boolean,
    val physicalIds: List<String>,
    val hwLevel: Int,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val sensorSize: SizeF?,
    val pixelArray: Size?,
    val activeArray: Rect?,
    val preCorrectionArray: Rect?,
    val isoRange: IntRange?,
    val exposureRangeNs: LongRange?,
    val maxFrameDurationNs: Long?,
    val maxAnalogIso: Int?,
    val manualSensor: Boolean,
    val manualPostProcessing: Boolean,
    val raw: Boolean,
    val jpegSizes: List<Size>,
    val yuvSizes: List<Size>,
    val rawSizes: List<Size>,
    val privSizes: List<Size>,
    val depthOutput: Boolean,
    val afModes: List<Int>,
    val minFocusDistance: Float,
    val oisModes: List<Int>,
    val tonemapModes: List<Int>,
    val edgeModes: List<Int>,
    val nrModes: List<Int>,
    val shadingMapModes: List<Int>,
    val awbModes: List<Int>,
    val faceDetectModes: List<Int>,
    val maxFaces: Int,
    val zoomRatioRange: ClosedFloatingPointRange<Float>?,
    val maxDigitalZoom: Float,
    val aeCompRange: IntRange,
    val aeCompStep: Float,
    val postRawBoostRange: IntRange?,
    val cfa: Int?,
    val whiteLevel: Int?,
    val sensorOrientation: Int,
    val fpsRanges: List<android.util.Range<Int>>,
    val colorModel: DngColorModel?,
    val characteristics: CameraCharacteristics,
) {
    val isBack get() = facing == CameraCharacteristics.LENS_FACING_BACK
    val supportsSrgbTonemap get() = CameraMetadata.TONEMAP_MODE_PRESET_CURVE in tonemapModes
    val supportsGammaTonemap get() = CameraMetadata.TONEMAP_MODE_GAMMA_VALUE in tonemapModes
    val supportsContrastCurve get() = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE in tonemapModes && maxTonemapPoints >= 2
    val maxTonemapPoints: Int get() = characteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: 0
    val edgeOff get() = CameraMetadata.EDGE_MODE_OFF in edgeModes
    val lensShadingMap get() = CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON in shadingMapModes
    val canDevelopRaw get() = raw && rawSizes.isNotEmpty() && colorModel != null && cfa != null && cfa <= 3 && lensShadingMap
    val maxRaw get() = rawSizes.maxByOrNull { it.width.toLong() * it.height }
    val maxYuv get() = yuvSizes.maxByOrNull { it.width.toLong() * it.height }
    val maxJpeg get() = jpegSizes.maxByOrNull { it.width.toLong() * it.height }

    fun exposureLimits(): ExposureLimits = ExposureLimits(
        minExposureS = (exposureRangeNs?.first ?: 100_000L) / 1e9,
        maxExposureS = (exposureRangeNs?.last ?: 100_000_000L) / 1e9,
        minIso = isoRange?.first ?: 100,
        maxIso = isoRange?.last ?: 1600,
        maxAnalogIso = maxAnalogIso ?: isoRange?.last ?: 1600,
        manualSensor = manualSensor,
    )

    /** Native 35mm-equivalent focal length at an output aspect ratio. */
    fun equivalentFocal(aspect: AspectRatio): Double? {
        val f = focalLengths.firstOrNull() ?: return null
        val s = sensorSize ?: return null
        // Scale physical size to the active array (pixel array may include masked borders).
        val pa = pixelArray
        val aa = activeArray
        val w = if (pa != null && aa != null && pa.width > 0) s.width * aa.width() / pa.width else s.width
        val h = if (pa != null && aa != null && pa.height > 0) s.height * aa.height() / pa.height else s.height
        return LensMath.equivalentFocal(f.toDouble(), w.toDouble(), h.toDouble(), aspect)
    }
}

data class DeviceCaps(
    val model: String,
    val manufacturer: String,
    val cameras: List<CameraInfo>,
    val physical: Map<String, CameraInfo>,
) {
    val back: List<CameraInfo> get() = cameras.filter { it.isBack }
    val front: CameraInfo? get() = cameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_FRONT }

    /** The default back camera (the first back id, which Android documents as the primary). */
    val primaryBack: CameraInfo? get() = back.firstOrNull()

    fun info(id: String): CameraInfo? = cameras.firstOrNull { it.id == id } ?: physical[id]

    /** Optical sources for virtual-lens planning, honest about which are logical multi-cameras. */
    fun opticalSources(aspect: AspectRatio): List<OpticalSource> = back.mapNotNull { c ->
        val f = c.focalLengths.firstOrNull() ?: return@mapNotNull null
        val s = c.sensorSize ?: return@mapNotNull null
        val aa = c.activeArray ?: return@mapNotNull null
        val pa = c.pixelArray ?: Size(aa.width(), aa.height())
        val sw = s.width * aa.width() / pa.width.coerceAtLeast(1)
        val sh = s.height * aa.height() / pa.height.coerceAtLeast(1)
        val phys = c.physicalIds.mapNotNull { pid ->
            val p = physical[pid] ?: return@mapNotNull null
            val eq = p.equivalentFocal(aspect) ?: return@mapNotNull null
            eq to (p.maxYuv?.width ?: p.activeArray?.width() ?: 4000)
        }
        val zr = c.zoomRatioRange
        OpticalSource(
            cameraId = c.id,
            focalLengthMm = f.toDouble(),
            sensorWidthMm = sw.toDouble(),
            sensorHeightMm = sh.toDouble(),
            pixelWidth = c.maxYuv?.width ?: aa.width(),
            pixelHeight = c.maxYuv?.height ?: aa.height(),
            zoomMin = (zr?.start ?: 1f).toDouble(),
            zoomMax = (zr?.endInclusive ?: c.maxDigitalZoom).toDouble().coerceAtLeast(1.0),
            isLogical = c.isLogical,
            physicalEquivalents = phys,
            supportsRaw = c.canDevelopRaw,
        )
    }

    companion object {
        fun discover(manager: CameraManager): DeviceCaps {
            val cams = ArrayList<CameraInfo>()
            val phys = HashMap<String, CameraInfo>()
            val ids = try { manager.cameraIdList.toList() } catch (_: Exception) { emptyList() }
            for (id in ids) {
                val info = try { read(id, manager.getCameraCharacteristics(id)) } catch (_: Exception) { null } ?: continue
                cams.add(info)
                for (pid in info.physicalIds) if (pid !in phys) {
                    try { phys[pid] = read(pid, manager.getCameraCharacteristics(pid)) } catch (_: Exception) {}
                }
            }
            return DeviceCaps(Build.MODEL ?: "unknown", Build.MANUFACTURER ?: "unknown", cams, phys)
        }

        private fun <T> CameraCharacteristics.g(k: CameraCharacteristics.Key<T>): T? = try { get(k) } catch (_: Exception) { null }

        private fun mat(t: ColorSpaceTransform?): Mat3? {
            t ?: return null
            val d = DoubleArray(9)
            for (r in 0 until 3) for (c in 0 until 3) d[r * 3 + c] = t.getElement(c, r).toDouble()
            return Mat3(d)
        }

        fun read(id: String, ch: CameraCharacteristics): CameraInfo {
            val caps = ch.g(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList() ?: emptyList()
            val map = ch.g(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            fun sizes(fmt: Int): List<Size> = try { map?.getOutputSizes(fmt)?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            val privSizes = try { map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            val logical = REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps
            val physicalIds = if (logical) try { ch.physicalCameraIds.toList() } catch (_: Exception) { emptyList() } else emptyList()
            val cm1 = mat(ch.g(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1))
            val colorModel = cm1?.let {
                try {
                    DngColorModel(
                        colorMatrix1 = it,
                        colorMatrix2 = mat(ch.g(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)),
                        forwardMatrix1 = mat(ch.g(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)),
                        forwardMatrix2 = mat(ch.g(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)),
                        calibration1 = mat(ch.g(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)) ?: Mat3.IDENTITY,
                        calibration2 = mat(ch.g(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)),
                        illuminant1 = ch.g(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1) ?: 21,
                        illuminant2 = ch.g(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt(),
                    ).also { m -> m.cameraToXyzD50(com.takehaire.alpha.imaging.color.Vec3(0.5, 1.0, 0.6)) } // validates invertibility
                } catch (_: Exception) {
                    null
                }
            }
            val zr = if (Build.VERSION.SDK_INT >= 30) ch.g(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
            val aeRange = ch.g(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val boost = ch.g(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)
            return CameraInfo(
                id = id,
                facing = ch.g(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK,
                isLogical = logical,
                physicalIds = physicalIds,
                hwLevel = ch.g(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
                focalLengths = ch.g(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList(),
                apertures = ch.g(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList(),
                sensorSize = ch.g(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
                pixelArray = ch.g(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE),
                activeArray = ch.g(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                preCorrectionArray = ch.g(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE),
                isoRange = ch.g(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { it.lower..it.upper },
                exposureRangeNs = ch.g(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { it.lower..it.upper },
                maxFrameDurationNs = ch.g(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION),
                maxAnalogIso = ch.g(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY),
                manualSensor = REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps,
                manualPostProcessing = REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps,
                raw = REQUEST_AVAILABLE_CAPABILITIES_RAW in caps,
                jpegSizes = sizes(ImageFormat.JPEG),
                yuvSizes = sizes(ImageFormat.YUV_420_888),
                rawSizes = sizes(ImageFormat.RAW_SENSOR),
                privSizes = privSizes,
                depthOutput = sizes(ImageFormat.DEPTH16).isNotEmpty(),
                afModes = ch.g(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList(),
                minFocusDistance = ch.g(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                oisModes = ch.g(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toList() ?: emptyList(),
                tonemapModes = ch.g(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.toList() ?: emptyList(),
                edgeModes = ch.g(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.toList() ?: emptyList(),
                nrModes = ch.g(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toList() ?: emptyList(),
                shadingMapModes = ch.g(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)?.toList() ?: emptyList(),
                awbModes = ch.g(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList(),
                faceDetectModes = ch.g(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)?.toList() ?: emptyList(),
                maxFaces = ch.g(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0,
                zoomRatioRange = zr?.let { it.lower..it.upper },
                maxDigitalZoom = ch.g(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f,
                aeCompRange = aeRange?.let { it.lower..it.upper } ?: 0..0,
                aeCompStep = ch.g(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 1f / 3f,
                postRawBoostRange = boost?.let { it.lower..it.upper },
                cfa = ch.g(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
                whiteLevel = ch.g(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
                sensorOrientation = ch.g(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                fpsRanges = ch.g(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() ?: emptyList(),
                colorModel = colorModel,
                characteristics = ch,
            )
        }
    }
}
