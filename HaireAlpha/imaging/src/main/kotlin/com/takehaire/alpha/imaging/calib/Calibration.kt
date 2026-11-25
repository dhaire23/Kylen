package com.takehaire.alpha.imaging.calib

import com.takehaire.alpha.imaging.lens.LensProfile
import com.takehaire.alpha.imaging.look.LookSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a calibration value was obtained. Never upgraded without real evidence. */
@Serializable
enum class CalibrationSource {
    /** Derived from Camera2 metadata (DNG matrices, noise profile, shading map) at runtime. */
    METADATA_DERIVED,
    /** Checked against synthetic targets (pipeline correctness only, not sensor accuracy). */
    SYNTHETIC_VALIDATED,
    /** Tuned to match supplied reference photographs (aesthetic, subjective). */
    REFERENCE_AESTHETIC,
    /** Measured on the physical device with a chart under controlled light. */
    PHYSICAL_MEASURED,
}

/** ISO → strength breakpoint (linear interpolation in log ISO). */
@Serializable
data class IsoPoint(val iso: Int, val value: Double)

/**
 * Per-sensor processing calibration, independent from creative looks. The default profile is used
 * for any physical camera without a specific entry; entries match on device model + camera id or
 * on focal-class.
 */
@Serializable
data class SensorProfile(
    val id: String,
    val version: Int,
    val source: CalibrationSource,
    val matchModel: String? = null,
    val matchCameraId: String? = null,
    /** Initial raw exposure normalisation; refined at runtime by preview anchoring (see docs). */
    val baselineExposureEv: Double = 0.0,
    /** Bounds for runtime anchoring relative to baseline, stops. */
    val anchorRangeEv: Double = 1.5,
    val nrLuma: List<IsoPoint> = listOf(IsoPoint(50, 0.15), IsoPoint(200, 0.25), IsoPoint(800, 0.45), IsoPoint(3200, 0.65), IsoPoint(12800, 0.8)),
    val nrChroma: List<IsoPoint> = listOf(IsoPoint(50, 0.45), IsoPoint(200, 0.55), IsoPoint(800, 0.7), IsoPoint(3200, 0.85), IsoPoint(12800, 0.95)),
    /** Sharpening multiplier by ISO (less sharpening as noise rises). */
    val sharpen: List<IsoPoint> = listOf(IsoPoint(50, 1.0), IsoPoint(400, 0.9), IsoPoint(1600, 0.7), IsoPoint(6400, 0.5)),
    /** Extra multiplier for digital crop (cropped images are not over-sharpened to compensate). */
    val cropSharpenFalloff: Double = 0.25,
    val notes: String = "",
) {
    fun at(points: List<IsoPoint>, iso: Int): Double {
        if (points.isEmpty()) return 0.0
        val sorted = points.sortedBy { it.iso }
        if (iso <= sorted.first().iso) return sorted.first().value
        if (iso >= sorted.last().iso) return sorted.last().value
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (iso in a.iso..b.iso) {
                val t = (kotlin.math.ln(iso.toDouble()) - kotlin.math.ln(a.iso.toDouble())) /
                    (kotlin.math.ln(b.iso.toDouble()) - kotlin.math.ln(a.iso.toDouble()))
                return a.value + (b.value - a.value) * t
            }
        }
        return sorted.last().value
    }
}

/**
 * Processing modes (Fn menu). They govern *processing*, never colour (colour = the look).
 */
@Serializable
enum class ProcessingMode(val label: String, val blurb: String) {
    NATURAL("ALPHA NATURAL", "Camera-like development: restrained NR and sharpening, no HDR."),
    PURE("PURE", "Lightest touch: minimal NR and sharpening, no grain or halation. Best for editing."),
    COMPUTATIONAL("COMPUTATIONAL", "More noise reduction and gentle local tone compression for tough light."),
}

data class ProcessingProfile(
    val nrScale: Double,
    val sharpenAmount: Double,
    val localTone: Double,
    val spatialEffects: Boolean,
    val preferRaw: Boolean,
)

object ProcessingModes {
    fun profile(mode: ProcessingMode): ProcessingProfile = when (mode) {
        ProcessingMode.NATURAL -> ProcessingProfile(1.0, 0.32, 0.0, true, true)
        ProcessingMode.PURE -> ProcessingProfile(0.55, 0.18, 0.0, false, true)
        ProcessingMode.COMPUTATIONAL -> ProcessingProfile(1.35, 0.30, 0.35, true, false)
    }
}

@Serializable
data class LookLibraryFile(val schema: Int, val version: Int, val looks: List<LookSpec>)

@Serializable
data class LensLibraryFile(val schema: Int, val version: Int, val lenses: List<LensProfile>)

@Serializable
data class CalibrationFile(val schema: Int, val version: Int, val profiles: List<SensorProfile>)

/**
 * Loads the versioned, independently updateable assets bundled as resources under /haire.
 */
object Assets {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    private fun read(path: String): String =
        Assets::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing asset $path")

    val looks: LookLibraryFile by lazy { json.decodeFromString(LookLibraryFile.serializer(), read("/haire/looks.json")) }
    val lenses: LensLibraryFile by lazy { json.decodeFromString(LensLibraryFile.serializer(), read("/haire/lenses.json")) }
    val calibration: CalibrationFile by lazy { json.decodeFromString(CalibrationFile.serializer(), read("/haire/calibration.json")) }

    fun look(id: String): LookSpec = looks.looks.firstOrNull { it.id == id } ?: looks.looks.first()
    val alphaNatural: LookSpec get() = look("alpha_natural")

    fun sensorProfile(model: String, cameraId: String): SensorProfile {
        val p = calibration.profiles
        return p.firstOrNull { it.matchModel == model && it.matchCameraId == cameraId }
            ?: p.firstOrNull { it.matchModel == model && it.matchCameraId == null }
            ?: p.first { it.id == "default" }
    }
}
