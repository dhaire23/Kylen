package com.takehaire.alpha.imaging.lens

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.sqrt

@Serializable
enum class AspectRatio(val w: Int, val h: Int, val label: String) {
    R3_2(3, 2, "3:2"), R4_3(4, 3, "4:3"), R16_9(16, 9, "16:9"), R1_1(1, 1, "1:1");

    val value: Double get() = w.toDouble() / h
}

@Serializable
enum class LensKind { PRIME, ZOOM }

@Serializable
enum class LensCategory { WIDE, STANDARD, PORTRAIT, TELE, MACRO }

/** A virtual lens profile (framing + depth simulation). Not an optical model of any real lens. */
@Serializable
data class LensProfile(
    val id: String,
    val name: String,
    val kind: LensKind,
    val minFocal: Double,
    val maxFocal: Double,
    val maxAperture: Double,
    val minAperture: Double = 16.0,
    val category: LensCategory,
    /** Optional, restrained rendering: corner falloff in stops at wide open (0 = clean). */
    val vignetteWideOpen: Double = 0.0,
    val note: String = "",
    val core: Boolean = true,
)

/**
 * A source of optical framing on the device. For a standalone physical camera [zoomMax] is the
 * acceptable digital crop; for a logical multi-camera the HAL handles optical switching inside
 * [zoomMin]..[zoomMax] and [physicalEquivalents] lists the known physical lens equivalents.
 */
data class OpticalSource(
    val cameraId: String,
    val focalLengthMm: Double,
    val sensorWidthMm: Double,
    val sensorHeightMm: Double,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val zoomMin: Double = 1.0,
    val zoomMax: Double = 1.0,
    val isLogical: Boolean = false,
    /** Equivalent focal lengths (3:2) of physical sensors behind a logical camera, with pixel widths. */
    val physicalEquivalents: List<Pair<Double, Int>> = emptyList(),
    val supportsRaw: Boolean = false,
)

enum class CropClass(val label: String) {
    NATIVE("Native optical"),
    MODERATE("Moderate crop"),
    SUBSTANTIAL("Substantial crop"),
    UNAVAILABLE("Not recommended"),
}

data class LensPlan(
    val source: OpticalSource,
    /** Zoom ratio to request on the source (CONTROL_ZOOM_RATIO or crop). */
    val zoomRatio: Double,
    /** Digital magnification relative to the physical sensor likely used. */
    val digitalCrop: Double,
    val cropClass: CropClass,
    /** Estimated output megapixels after cropping at the chosen aspect. */
    val effectiveMegapixels: Double,
    val targetEquivalent: Double,
)

object LensMath {
    private const val FF_W = 36.0
    private const val FF_H = 24.0

    /** Largest rectangle of the given aspect inside w×h. */
    fun fitAspect(w: Double, h: Double, aspect: Double): Pair<Double, Double> =
        if (w / h > aspect) Pair(h * aspect, h) else Pair(w, w / aspect)

    /**
     * 35 mm-equivalent focal length for a given output aspect: ratio of the diagonals of the
     * full-frame and sensor regions after both are cropped to the same aspect ratio.
     */
    fun equivalentFocal(focalMm: Double, sensorW: Double, sensorH: Double, aspect: AspectRatio): Double {
        val (sw, sh) = fitAspect(sensorW, sensorH, aspect.value)
        val (fw, fh) = fitAspect(FF_W, FF_H, aspect.value)
        return focalMm * hypot(fw, fh) / hypot(sw, sh)
    }

    fun outputMegapixels(pxW: Int, pxH: Int, aspect: AspectRatio, crop: Double): Double {
        val (w, h) = fitAspect(pxW.toDouble(), pxH.toDouble(), aspect.value)
        return w * h / (crop * crop) / 1e6
    }

    fun classify(crop: Double, megapixels: Double): CropClass = when {
        crop <= 1.08 -> CropClass.NATIVE
        megapixels < 2.0 || crop > 4.0 -> CropClass.UNAVAILABLE
        crop <= 1.7 -> CropClass.MODERATE
        else -> CropClass.SUBSTANTIAL
    }

    /**
     * Chooses the source and zoom ratio for a target equivalent focal length, preferring the least
     * digital crop on real optics, and never "zooming out" beyond a sensor's native field of view.
     */
    fun plan(targetEq: Double, aspect: AspectRatio, sources: List<OpticalSource>): LensPlan? {
        var best: LensPlan? = null
        var bestScore = Double.MAX_VALUE
        for (s in sources) {
            val native = equivalentFocal(s.focalLengthMm, s.sensorWidthMm, s.sensorHeightMm, aspect)
            val zoom = targetEq / native
            if (zoom < s.zoomMin * 0.98 || zoom > s.zoomMax * 1.02) continue
            // Which physical sensor will most likely deliver this framing?
            var physEq = native
            var physPx = s.pixelWidth
            if (s.isLogical && s.physicalEquivalents.isNotEmpty()) {
                val candidates = s.physicalEquivalents.filter { it.first <= targetEq * 1.02 }
                val chosen = candidates.maxByOrNull { it.first } ?: s.physicalEquivalents.minBy { it.first }
                physEq = chosen.first
                physPx = chosen.second
            }
            val crop = (targetEq / physEq).coerceAtLeast(1.0)
            val pxH = (physPx * s.pixelHeight.toDouble() / s.pixelWidth).toInt()
            val mp = outputMegapixels(physPx, pxH, aspect, crop)
            val cls = classify(crop, mp)
            val score = crop + (if (s.isLogical) 0.01 else 0.0) + (if (cls == CropClass.UNAVAILABLE) 100.0 else 0.0)
            if (score < bestScore) {
                bestScore = score
                best = LensPlan(s, zoom.coerceIn(s.zoomMin, s.zoomMax), crop, cls, mp, targetEq)
            }
        }
        return best
    }

    /**
     * Centre crop rectangle (in source pixels) for an aspect ratio and digital zoom.
     * Returns x, y, w, h with even offsets so CFA phase is preserved.
     */
    fun cropRect(srcW: Int, srcH: Int, aspect: AspectRatio, zoom: Double): IntArray {
        // The sensor is landscape; output aspect is applied in sensor orientation and the result is
        // rotated afterwards for portrait shooting.
        val (fw, fh) = fitAspect(srcW.toDouble(), srcH.toDouble(), aspect.value)
        val w = ((fw / zoom).toInt() and 0x7FFFFFFE).coerceAtLeast(2)
        val h = ((fh / zoom).toInt() and 0x7FFFFFFE).coerceAtLeast(2)
        val x = ((srcW - w) / 2) and 0x7FFFFFFE
        val y = ((srcH - h) / 2) and 0x7FFFFFFE
        return intArrayOf(x, y, w, h)
    }

    /** Circle-of-confusion based blur radius (fraction of image diagonal) for virtual depth of field. */
    fun blurFraction(focalEq: Double, aperture: Double, subjectDistM: Double, bgDistM: Double): Double {
        // Thin lens on a full-frame reference: blur disc diameter on sensor c = f² (D−s) / (N s (D − f))
        val f = focalEq / 1000.0
        val s = subjectDistM.coerceAtLeast(f * 1.5)
        val d = bgDistM.coerceAtLeast(s)
        val c = f * f * (d - s) / (aperture * s * (d - f))
        return c / (sqrt(FF_W * FF_W + FF_H * FF_H) / 1000.0)
    }
}
