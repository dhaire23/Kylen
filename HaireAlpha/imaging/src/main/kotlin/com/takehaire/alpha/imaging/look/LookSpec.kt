package com.takehaire.alpha.imaging.look

import com.takehaire.alpha.imaging.tone.ToneParams
import kotlinx.serialization.Serializable

@Serializable
enum class LookCategory { CREATIVE, FILM, SIGNATURE, CUSTOM }

/** Selective adjustment in Oklch around a hue centre (degrees). */
@Serializable
data class HueBand(
    val center: Double,
    val width: Double = 40.0,
    val hueShift: Double = 0.0,
    val sat: Double = 1.0,
    val light: Double = 0.0,
)

/** Additive tint in Oklab (amount ≈ chroma units, 0.01 is subtle). */
@Serializable
data class Tint(val hue: Double, val amount: Double)

/** Monochrome conversion: spectral-filter-like channel weights on scene linear Rec.2020. */
@Serializable
data class MonoSpec(
    val red: Double = 0.30,
    val green: Double = 0.59,
    val blue: Double = 0.11,
    val toneHue: Double = 60.0,
    val toneAmount: Double = 0.0,
)

/** Film grain. amount: rms in encoded units at mid-tones; size: grain diameter as fraction of 1/1000 short side. */
@Serializable
data class GrainSpec(val amount: Double = 0.0, val size: Double = 1.0, val roughness: Double = 0.5)

/** Halation: red-orange glow around strong highlights. radius is a fraction of the short side. */
@Serializable
data class HalationSpec(
    val amount: Double = 0.0,
    val threshold: Double = 2.5,
    val radius: Double = 0.012,
    val hue: Double = 30.0,
)

/**
 * A complete photographic look. Everything colour-related is baked into a 3D LUT; grain,
 * halation and vignette are spatial stages applied by the still pipeline (and approximated in preview).
 */
@Serializable
data class LookSpec(
    val id: String,
    val name: String,
    val category: LookCategory,
    val description: String = "",
    val inspiredBy: String = "",
    val version: Int = 1,
    val tone: ToneParams = ToneParams(),
    /** 1 = hue-preserving tone mapping (norm based), 0 = per-channel (film-like highlight desaturation). */
    val hueRestore: Double = 0.75,
    /** Path-to-white strength for very bright saturated colours. */
    val highlightDesat: Double = 0.5,
    /** Optional 3x3 creative matrix on scene-linear Rec.2020, rows are normalised to keep neutrals. */
    val creativeMatrix: List<Double>? = null,
    /** Amber(+)/blue(−) scene balance offset, ≈ hundreds of Kelvin at 5500K. */
    val warmth: Double = 0.0,
    /** Magenta(+)/green(−) offset. */
    val tint: Double = 0.0,
    val saturation: Double = 1.0,
    /** 0..1 compression of already-high chroma (keeps colour rich without neon). */
    val chromaCompression: Double = 0.0,
    /** Lifts saturation of low-chroma colours more than high chroma colours. */
    val vibrance: Double = 0.0,
    val hueBands: List<HueBand> = emptyList(),
    val shadowTint: Tint? = null,
    val highlightTint: Tint? = null,
    /** 0..1 limit on how far hue/chroma operations may move skin tones. 1 = fully protected. */
    val skinProtect: Double = 0.85,
    val monochrome: MonoSpec? = null,
    val grain: GrainSpec? = null,
    val halation: HalationSpec? = null,
    /** Vignette strength in stops at the corners (positive darkens). */
    val vignette: Double = 0.0,
    /** Multiplier on the processing mode's sharpening. */
    val sharpening: Double = 1.0,
    /** Multiplier on local contrast ("clarity"); 0 disables. */
    val clarity: Double = 0.0,
    /**
     * Film-stock colour balance. When set and the photographer uses AWB, the camera white balance is
     * fixed to this Kelvin value (e.g. 3200 K for a tungsten-balanced stock): neutral under matching
     * light, colour-shifted under other light, exactly like shooting that film.
     */
    val filmWhiteBalanceK: Double? = null,
)

/** User recipe adjustments layered on top of a factory look. All 0 = factory look. */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val baseLookId: String,
    val contrast: Double = 0.0, // -1..1
    val highlights: Double = 0.0, // -1..1
    val shadows: Double = 0.0, // -1..1
    val saturation: Double = 0.0, // -1..1
    val temperature: Double = 0.0, // -1..1
    val tint: Double = 0.0, // -1..1
    val grain: Double = 0.0, // 0..1
    val clarity: Double = 0.0, // -1..1
    val sharpness: Double = 0.0, // -1..1
    val fade: Double = 0.0, // 0..1
    val halation: Double = 0.0, // 0..1
    val vignette: Double = 0.0, // -1..1
    val cubeLutFile: String? = null,
)

fun LookSpec.withRecipe(r: Recipe): LookSpec {
    val t = tone
    return copy(
        id = r.id,
        name = r.name,
        category = LookCategory.CUSTOM,
        tone = t.copy(
            contrast = (t.contrast * (1.0 + 0.35 * r.contrast)).coerceIn(0.5, 2.0),
            shadowDepth = (t.shadowDepth - 0.6 * r.shadows).coerceIn(-0.8, 1.2),
            shoulder = (t.shoulder - 0.4 * r.highlights).coerceIn(0.0, 1.0),
            whiteClip = (t.whiteClip + 0.04 * r.highlights.coerceAtMost(0.0)).coerceIn(0.85, 1.0),
            blackLift = (t.blackLift + 0.08 * r.fade).coerceIn(0.0, 0.15),
        ),
        saturation = (saturation * (1.0 + 0.5 * r.saturation)).coerceIn(0.0, 2.0),
        warmth = warmth + 6.0 * r.temperature,
        tint = tint + 4.0 * r.tint,
        grain = if (r.grain > 0.0) {
            val g = grain ?: GrainSpec(0.0, 1.0, 0.5)
            g.copy(amount = g.amount + 0.035 * r.grain)
        } else grain,
        halation = if (r.halation > 0.0) {
            val h = halation ?: HalationSpec()
            h.copy(amount = h.amount + 0.25 * r.halation)
        } else halation,
        vignette = vignette + 0.8 * r.vignette,
        sharpening = (sharpening * (1.0 + 0.8 * r.sharpness)).coerceIn(0.0, 2.5),
        clarity = clarity + 0.5 * r.clarity,
    )
}
