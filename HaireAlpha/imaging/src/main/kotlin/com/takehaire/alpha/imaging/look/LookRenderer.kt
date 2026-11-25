package com.takehaire.alpha.imaging.look

import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.Mat3
import com.takehaire.alpha.imaging.tone.ToneCurve
import com.takehaire.alpha.imaging.tone.ToneParams
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The colour half of the imaging engine: scene-linear Rec.2020 → sRGB-encoded display value for
 * one look. This is evaluated only while baking LUTs (see [LutBaker]); per-pixel work at capture
 * time is a single tetrahedral LUT lookup, which also guarantees that preview and still share the
 * exact same colour transform.
 *
 * Order of operations (each applied exactly once):
 *  1. creative balance (warmth/tint) and creative matrix — scene linear
 *  2. tone mapping (hue-preserving norm blended with per-channel) — scene → display linear
 *  3. perceptual colour operations in Oklab/Oklch (saturation, hue bands, split tone, skin protection)
 *  4. perceptual gamut mapping into sRGB (chroma reduction at constant lightness and hue)
 *  5. sRGB OETF encode
 *
 * @param displayReferredInput when the input has already been tone mapped by the camera vendor
 *        (processed YUV/JPEG without a controllable tone curve), the look's curve is applied
 *        relative to the house reference curve instead of on top of it, avoiding double contrast.
 */
class LookRenderer(
    val look: LookSpec,
    val whiteEv: Double,
    val displayReferredInput: Boolean = false,
    referenceTone: ToneParams = ToneParams(),
) {
    val curve = ToneCurve(look.tone, whiteEv)
    private val refCurve = ToneCurve(referenceTone, whiteEv)

    private val balance: Mat3 = balanceMatrix(look.warmth, look.tint)
    private val creative: Mat3? = look.creativeMatrix?.let { Mat3(it.toDoubleArray()).rowNormalized() }
    private val sceneMatrix: Mat3 = creative?.let { it * balance } ?: balance
    private val m = sceneMatrix.toDoubleArray()
    private val toSrgb = ColorMath.REC2020_TO_SRGB.toDoubleArray()
    private val yW = ColorMath.REC2020_Y

    // Tone lookup tables (speed only; exact functions are monotone and smooth).
    private val evMin = -14.0
    private val evStep = 1.0 / 128.0
    private val sceneTable: DoubleArray
    private val dispTable: DoubleArray? // encoded input → display linear
    private val monoWeights: DoubleArray?

    init {
        val n = ((whiteEv.coerceIn(1.5, 10.0) + 0.5 - evMin) / evStep).toInt() + 2
        sceneTable = DoubleArray(n) { i -> curve.displayLinear(0.18 * Math.pow(2.0, evMin + i * evStep)) }
        dispTable = if (displayReferredInput) {
            DoubleArray(4097) { i ->
                val e = i / 4096.0
                if (e <= 0.0) ColorMath.srgbDecode(look.tone.blackLift)
                else {
                    val ev = refCurve.inverseEncoded(e)
                    if (e >= 0.9999) ColorMath.srgbDecode(curve.encoded(whiteEv))
                    else ColorMath.srgbDecode(curve.encoded(ev))
                }
            }
        } else null
        monoWeights = look.monochrome?.let { ms ->
            val s = ms.red + ms.green + ms.blue
            doubleArrayOf(ms.red / s, ms.green / s, ms.blue / s)
        }
    }

    private fun tone(v: Double): Double {
        if (v <= 0.0) return sceneTable[0]
        val dt = dispTable
        if (dt != null) {
            val e = ColorMath.srgbEncode(v) * 4096.0
            val i = e.toInt().coerceIn(0, 4095)
            val f = (e - i).coerceIn(0.0, 1.0)
            return dt[i] + (dt[i + 1] - dt[i]) * f
        }
        val ev = ln(v / 0.18) / LN2
        val p = (ev - evMin) / evStep
        if (p <= 0) return sceneTable[0]
        val i = p.toInt()
        if (i >= sceneTable.size - 1) return sceneTable.last()
        val f = p - i
        return sceneTable[i] + (sceneTable[i + 1] - sceneTable[i]) * f
    }

    private val lab = DoubleArray(3)
    private val rgb = DoubleArray(3)

    /** Not thread-safe (scratch buffers); create one renderer per thread. */
    fun render(r0: Double, g0: Double, b0: Double, out: DoubleArray) {
        val r = m[0] * r0 + m[1] * g0 + m[2] * b0
        val g = m[3] * r0 + m[4] * g0 + m[5] * b0
        val b = m[6] * r0 + m[7] * g0 + m[8] * b0

        val mono = monoWeights
        if (mono != null) {
            renderMono(mono[0] * r + mono[1] * g + mono[2] * b, out)
            return
        }

        // ---- 2. tone mapping
        val rc = r.coerceAtLeast(0.0)
        val gc = g.coerceAtLeast(0.0)
        val bc = b.coerceAtLeast(0.0)
        val num = yW[0] * rc * rc * rc + yW[1] * gc * gc * gc + yW[2] * bc * bc * bc
        val den = yW[0] * rc * rc + yW[1] * gc * gc + yW[2] * bc * bc
        val norm = if (den > 1e-20) num / den else 0.0
        var tr: Double
        var tg: Double
        var tb: Double
        if (norm <= 1e-12) {
            val black = tone(0.0)
            tr = black; tg = black; tb = black
        } else {
            val yN = tone(norm)
            val ratio = yN / norm
            val hr = rc * ratio
            val hg = gc * ratio
            val hb = bc * ratio
            val pr = tone(rc)
            val pg = tone(gc)
            val pb = tone(bc)
            val k = look.hueRestore
            tr = pr + (hr - pr) * k
            tg = pg + (hg - pg) * k
            tb = pb + (hb - pb) * k
            val w = ColorMath.smoothstep(0.55, 1.0, yN) * look.highlightDesat
            if (w > 0) {
                val y = yW[0] * tr + yW[1] * tg + yW[2] * tb
                tr += (y - tr) * w; tg += (y - tg) * w; tb += (y - tb) * w
            }
        }

        // ---- 3. perceptual colour in Oklab (linear sRGB primaries, may be out of gamut)
        val sr = toSrgb[0] * tr + toSrgb[1] * tg + toSrgb[2] * tb
        val sg = toSrgb[3] * tr + toSrgb[4] * tg + toSrgb[5] * tb
        val sb = toSrgb[6] * tr + toSrgb[7] * tg + toSrgb[8] * tb
        ColorMath.linearSrgbToOklab(sr, sg, sb, lab)
        var L = lab[0]
        val c0 = hypot(lab[1], lab[2])
        val h0 = ColorMath.hueDeg(lab[1], lab[2])
        var c = c0
        var h = h0

        c *= look.saturation
        if (look.vibrance != 0.0) c *= 1.0 + look.vibrance * (1.0 - ColorMath.smoothstep(0.0, 0.18, c))
        if (look.chromaCompression > 0.0) c /= (1.0 + look.chromaCompression * c / 0.22)

        val neutralFade = ColorMath.smoothstep(0.008, 0.04, c0)
        for (band in look.hueBands) {
            val w = bandWeight(h0, band.center, band.width) * neutralFade
            if (w <= 0.0) continue
            h += band.hueShift * w
            c *= 1.0 + (band.sat - 1.0) * w
            L += band.light * w * ColorMath.smoothstep(0.05, 0.3, L)
        }

        var a = c * cos(h * DEG)
        var bb = c * sin(h * DEG)
        look.shadowTint?.let { t ->
            val wS = 1.0 - ColorMath.smoothstep(0.12, 0.62, L)
            a += t.amount * wS * cos(t.hue * DEG); bb += t.amount * wS * sin(t.hue * DEG)
        }
        look.highlightTint?.let { t ->
            val wH = ColorMath.smoothstep(0.45, 0.95, L) * (1.0 - ColorMath.smoothstep(0.97, 1.0, L))
            a += t.amount * wH * cos(t.hue * DEG); bb += t.amount * wH * sin(t.hue * DEG)
        }

        // Skin protection: bound how far the look moved hue/chroma of skin-like colours.
        if (look.skinProtect > 0.0) {
            val ws = skinWeight(h0, c0, L) * look.skinProtect
            if (ws > 0.0) {
                val c1 = hypot(a, bb)
                val h1 = ColorMath.hueDeg(a, bb)
                var dh = h1 - h0
                if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
                val dhClamped = dh.coerceIn(-SKIN_MAX_HUE_SHIFT, SKIN_MAX_HUE_SHIFT)
                val ratio = if (c0 > 1e-6) c1 / c0 else 1.0
                val ratioClamped = ratio.coerceIn(SKIN_MIN_CHROMA, SKIN_MAX_CHROMA)
                val hF = h0 + dh + (dhClamped - dh) * ws
                val cF = c0 * (ratio + (ratioClamped - ratio) * ws)
                a = cF * cos(hF * DEG); bb = cF * sin(hF * DEG)
            }
        }
        gamutMapEncode(L, a, bb, out)
    }

    private fun renderMono(y0: Double, out: DoubleArray) {
        val g = tone(y0.coerceAtLeast(0.0))
        val L = cbrt(g.coerceAtLeast(0.0))
        var a = 0.0
        var b = 0.0
        val ms = look.monochrome!!
        if (ms.toneAmount != 0.0) {
            val wm = ColorMath.smoothstep(0.05, 0.4, L) * (1.0 - ColorMath.smoothstep(0.8, 1.0, L))
            a += ms.toneAmount * wm * cos(ms.toneHue * DEG); b += ms.toneAmount * wm * sin(ms.toneHue * DEG)
        }
        look.shadowTint?.let { t ->
            val wS = 1.0 - ColorMath.smoothstep(0.12, 0.62, L)
            a += t.amount * wS * cos(t.hue * DEG); b += t.amount * wS * sin(t.hue * DEG)
        }
        look.highlightTint?.let { t ->
            val wH = ColorMath.smoothstep(0.45, 0.95, L) * (1.0 - ColorMath.smoothstep(0.97, 1.0, L))
            a += t.amount * wH * cos(t.hue * DEG); b += t.amount * wH * sin(t.hue * DEG)
        }
        gamutMapEncode(L, a, b, out)
    }

    private fun inGamut(L: Double, a: Double, b: Double): Boolean {
        ColorMath.oklabToLinearSrgb(L, a, b, rgb)
        val e = 1e-6
        return rgb[0] >= -e && rgb[1] >= -e && rgb[2] >= -e && rgb[0] <= 1 + e && rgb[1] <= 1 + e && rgb[2] <= 1 + e
    }

    /**
     * Soft perceptual gamut mapping: find the maximum in-gamut chroma at this lightness/hue and
     * compress the outer 20 % smoothly (tanh knee) so saturated colours never hard-clip or skew hue.
     */
    private fun gamutMapEncode(L0: Double, a0: Double, b0: Double, out: DoubleArray) {
        val L = L0.coerceIn(0.0, 1.0)
        val c = hypot(a0, b0)
        if (L >= 0.99999 || c < 1e-9) {
            ColorMath.oklabToLinearSrgb(L, 0.0, 0.0, rgb)
            for (i in 0..2) out[i] = ColorMath.srgbEncode(rgb[i].coerceIn(0.0, 1.0))
            return
        }
        val ua = a0 / c
        val ub = b0 / c
        var cmax: Double
        if (inGamut(L, ua * c * 1.25, ub * c * 1.25)) {
            cmax = c * 1.25
        } else {
            var lo = 0.0
            var hi = c * 1.25
            repeat(22) {
                val mid = (lo + hi) / 2
                if (inGamut(L, ua * mid, ub * mid)) lo = mid else hi = mid
            }
            cmax = lo
        }
        val knee = 0.8 * cmax
        val cOut = if (c <= knee || cmax <= 1e-9) c.coerceAtMost(cmax) else {
            val span = cmax - knee
            knee + span * tanh((c - knee) / span)
        }
        ColorMath.oklabToLinearSrgb(L, ua * cOut, ub * cOut, rgb)
        for (i in 0..2) out[i] = ColorMath.srgbEncode(rgb[i].coerceIn(0.0, 1.0))
    }

    companion object {
        private const val LN2 = 0.6931471805599453
        private const val DEG = PI / 180.0
        // Monk Skin Tone swatches span Oklch hue ≈ 43°–85° and chroma ≈ 0.01–0.07 (deepest tones are
        // nearly neutral, which makes them the most vulnerable to tints turning them grey or ashy).
        const val SKIN_HUE_CENTER = 64.0
        const val SKIN_HUE_HALF_WIDTH = 42.0
        const val SKIN_MAX_HUE_SHIFT = 4.0
        const val SKIN_MIN_CHROMA = 0.88
        const val SKIN_MAX_CHROMA = 1.12

        /** Raised-cosine weight, 1 at centre, 0 beyond width/2 . */
        fun bandWeight(h: Double, center: Double, width: Double): Double {
            val d = ColorMath.circularDistanceDeg(h, center)
            val half = width / 2.0
            if (d >= half) return 0.0
            return 0.5 + 0.5 * cos(PI * d / half)
        }

        /** Membership of the skin-tone region in Oklch (flat-topped hue band, low-to-moderate chroma). */
        fun skinWeight(h: Double, c: Double, L: Double): Double {
            val d = ColorMath.circularDistanceDeg(h, SKIN_HUE_CENTER)
            val hw = 1.0 - ColorMath.smoothstep(SKIN_HUE_HALF_WIDTH * 0.6, SKIN_HUE_HALF_WIDTH, d)
            val cw = ColorMath.smoothstep(0.004, 0.014, c) * (1.0 - ColorMath.smoothstep(0.16, 0.24, c))
            val lw = ColorMath.smoothstep(0.12, 0.22, L) * (1.0 - ColorMath.smoothstep(0.95, 0.99, L))
            return hw * cw * lw
        }

        /**
         * Creative white balance shift as a Bradford adaptation in Rec.2020, luminance preserving.
         * warmth: +1 ≈ +100 K of "warming" relative to 5500 K; tint: +1 ≈ +0.001 Duv of magenta.
         */
        fun balanceMatrix(warmth: Double, tint: Double): Mat3 {
            if (warmth == 0.0 && tint == 0.0) return Mat3.IDENTITY
            val ref = ColorMath.cctToXy(5500.0)
            val src = ColorMath.cctToXy(5500.0 + 100.0 * warmth, 0.001 * tint)
            val ca = ColorMath.bradford(ColorMath.xyToXYZ(src[0], src[1]), ColorMath.xyToXYZ(ref[0], ref[1]))
            val mat = ColorMath.XYZ_TO_REC2020 * ca * ColorMath.REC2020_TO_XYZ
            val white = mat * com.takehaire.alpha.imaging.color.Vec3.ONE
            val y = ColorMath.REC2020_Y[0] * white.x + ColorMath.REC2020_Y[1] * white.y + ColorMath.REC2020_Y[2] * white.z
            return mat.scale(1.0 / y)
        }
    }
}
