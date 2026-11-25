package com.takehaire.alpha.imaging.process

import com.takehaire.alpha.imaging.color.ColorMath
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computational depth of field (EXPERIMENTAL). Inputs are the finished photograph and a subject
 * confidence mask from a segmentation model. There is no true depth map: the background blur grows
 * smoothly with distance from the subject, so it is never a flat, uniform blur, and the blur radius is
 * derived from thin-lens optics for the virtual lens (focal length, virtual aperture, focus distance).
 *
 * Quality protections: blur is gathered in linear light with mask-weighted sampling (no subject-colour
 * halo), the mask is refined edge-aware against the photo (hair, glasses), the subject is never
 * touched, and the effect is skipped when the mask is not a confident, plausible subject.
 */
object Bokeh {

    data class Params(
        /** Blur disc diameter as a fraction of the image diagonal for the farthest background. */
        val blurFraction: Double,
        /** Long side of the internal working image (speed vs. quality). */
        val workLongSide: Int = 1200,
        val samples: Int = 40,
        val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    )

    data class Result(val applied: Boolean, val reason: String, val coverage: Double, val radiusPx: Double)

    private val decode = FloatArray(256) { ColorMath.srgbDecodeF(it / 255f) }
    private val encode = IntArray(4097) { (ColorMath.srgbEncode(it / 4096.0) * 255 + 0.5).toInt().coerceIn(0, 255) }

    private fun enc(v: Float): Int = encode[(v.coerceIn(0f, 1f) * 4096f).toInt()]

    /**
     * Applies the effect to [argb] (w×h, modified in place).
     * @param mask subject confidence 0..1 at mw×mh (any resolution, same framing)
     */
    fun apply(argb: IntArray, w: Int, h: Int, mask: FloatArray, mw: Int, mh: Int, p: Params): Result {
        require(argb.size == w * h && mask.size == mw * mh)
        // ---- working resolution
        val s = max(1, (max(w, h) + p.workLongSide - 1) / p.workLongSide)
        val ww = w / s
        val wh = h / s
        val lin = FloatArray(ww * wh * 3)
        for (y in 0 until wh) for (x in 0 until ww) {
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until s) for (i in 0 until s) {
                val px = argb[(y * s + j) * w + x * s + i]
                r += decode[(px shr 16) and 0xFF]; g += decode[(px shr 8) and 0xFF]; b += decode[px and 0xFF]
            }
            val n = (s * s).toFloat()
            val o = (y * ww + x) * 3
            lin[o] = r / n; lin[o + 1] = g / n; lin[o + 2] = b / n
        }
        // ---- mask at working resolution, refined edge-aware against the photo
        var m = FloatArray(ww * wh) { i ->
            sampleBilinear(mask, mw, mh, (i % ww + 0.5f) / ww * mw - 0.5f, (i / ww + 0.5f) / wh * mh - 0.5f)
        }
        val guide = FloatArray(ww * wh) { i -> sqrt(0.2126f * lin[i * 3] + 0.7152f * lin[i * 3 + 1] + 0.0722f * lin[i * 3 + 2]) }
        m = guidedFilter(guide, m, ww, wh, (max(ww, wh) / 300f).coerceAtLeast(2f), 1e-3f)
        for (i in m.indices) m[i] = smooth(0.3f, 0.7f, m[i])

        // ---- confidence checks (prefer the original photo over a bad cut-out)
        val coverage = m.average()
        if (coverage < 0.02) return Result(false, "no clear subject", coverage, 0.0)
        if (coverage > 0.85) return Result(false, "subject fills the frame", coverage, 0.0)
        val decided = m.count { it < 0.1f || it > 0.9f }.toDouble() / m.size
        if (decided < 0.85) return Result(false, "subject edges uncertain", coverage, 0.0)

        // ---- blur radius from optics, depth-like falloff from the subject
        val diag = sqrt((ww * ww + wh * wh).toDouble())
        val rMax = (p.blurFraction * diag / 2).coerceIn(0.0, 48.0)
        if (rMax < 1.0) return Result(false, "aperture too small for visible blur", coverage, rMax * s)
        val near = m.copyOf()
        StillDeveloper.gaussianBlur(near, ww, wh, (0.06 * diag).toFloat().coerceIn(2f, 64f))
        val depth = FloatArray(ww * wh) { i -> (1f - min(1f, near[i] * 4f)).let { it * it * (3 - 2 * it) } } // 0 next to subject … 1 far

        val levels = listOf(0.35, 1.0).map { f -> blurLevel(lin, m, ww, wh, rMax * f, p) }

        // ---- composite at full resolution
        val pool = Executors.newFixedThreadPool(p.threads)
        try {
            pool.invokeAll((0 until p.threads).map { t ->
                Callable {
                    var y = t
                    while (y < h) {
                        val fy = (y + 0.5f) / s - 0.5f
                        for (x in 0 until w) {
                            val fx = (x + 0.5f) / s - 0.5f
                            val mv = sampleBilinear(m, ww, wh, fx, fy)
                            if (mv >= 0.999f) continue
                            val d = sampleBilinear(depth, ww, wh, fx, fy)
                            val i = y * w + x
                            val px = argb[i]
                            var r = decode[(px shr 16) and 0xFF]
                            var g = decode[(px shr 8) and 0xFF]
                            var b = decode[px and 0xFF]
                            val a0 = sampleBilinear3(levels[0], ww, wh, fx, fy, 0)
                            val a1 = sampleBilinear3(levels[0], ww, wh, fx, fy, 1)
                            val a2 = sampleBilinear3(levels[0], ww, wh, fx, fy, 2)
                            val bg0 = a0 + (sampleBilinear3(levels[1], ww, wh, fx, fy, 0) - a0) * d
                            val bg1 = a1 + (sampleBilinear3(levels[1], ww, wh, fx, fy, 1) - a1) * d
                            val bg2 = a2 + (sampleBilinear3(levels[1], ww, wh, fx, fy, 2) - a2) * d
                            r = mv * r + (1 - mv) * bg0
                            g = mv * g + (1 - mv) * bg1
                            b = mv * b + (1 - mv) * bg2
                            argb[i] = (px and 0xFF000000.toInt()) or (enc(r) shl 16) or (enc(g) shl 8) or enc(b)
                        }
                        y += p.threads
                    }
                }
            }).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        return Result(true, "applied", coverage, rMax * s)
    }

    /**
     * Disc blur by golden-angle sampling in linear light, gathering only background (mask-weighted).
     * Weighted colour P = rgb·w and weight W are first pre-filtered to the sample spacing, so every
     * sample represents its patch of the disc: small bright lights become complete, smooth discs
     * instead of speckles, regardless of radius.
     */
    private fun blurLevel(lin: FloatArray, m: FloatArray, w: Int, h: Int, radius: Double, p: Params): FloatArray {
        val n = p.samples
        val pr = FloatArray(w * h); val pg = FloatArray(w * h); val pb = FloatArray(w * h); val pw = FloatArray(w * h)
        for (q in 0 until w * h) {
            val o = q * 3
            val lum = 0.2126f * lin[o] + 0.7152f * lin[o + 1] + 0.0722f * lin[o + 2]
            // Bright points spread into visible discs, like real out-of-focus highlights.
            val wt = (1f - m[q]).coerceAtLeast(0f) * (1f + 3f * max(0f, lum - 0.75f))
            pr[q] = lin[o] * wt; pg[q] = lin[o + 1] * wt; pb[q] = lin[o + 2] * wt; pw[q] = wt
        }
        val spacing = (radius * sqrt(PI / n)).toFloat()
        if (spacing > 1f) for (a in listOf(pr, pg, pb, pw)) StillDeveloper.gaussianBlur(a, w, h, spacing * 0.5f)
        val ox = FloatArray(n); val oy = FloatArray(n)
        val golden = PI * (3 - sqrt(5.0))
        for (k in 0 until n) {
            val rr = radius * sqrt((k + 0.5) / n)
            ox[k] = (rr * cos(k * golden)).toFloat(); oy[k] = (rr * sin(k * golden)).toFloat()
        }
        val out = FloatArray(lin.size)
        val pool = Executors.newFixedThreadPool(p.threads)
        try {
            pool.invokeAll((0 until p.threads).map { t ->
                Callable {
                    var y = t
                    while (y < h) {
                        for (x in 0 until w) {
                            var r = 0f; var g = 0f; var b = 0f; var ws = 0f
                            for (k in 0 until n) {
                                val sx = (x + ox[k]).toInt().coerceIn(0, w - 1)
                                val sy = (y + oy[k]).toInt().coerceIn(0, h - 1)
                                val q = sy * w + sx
                                r += pr[q]; g += pg[q]; b += pb[q]; ws += pw[q]
                            }
                            val o = (y * w + x) * 3
                            if (ws > 1e-4f) { out[o] = r / ws; out[o + 1] = g / ws; out[o + 2] = b / ws }
                            else { out[o] = lin[o]; out[o + 1] = lin[o + 1]; out[o + 2] = lin[o + 2] }
                        }
                        y += p.threads
                    }
                }
            }).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        return out
    }

    /** Cross guided filter (He et al.): smooths p while following edges of guide I. */
    fun guidedFilter(I: FloatArray, pIn: FloatArray, w: Int, h: Int, sigma: Float, eps: Float): FloatArray {
        fun blur(a: FloatArray) = a.copyOf().also { StillDeveloper.gaussianBlur(it, w, h, sigma) }
        val mI = blur(I)
        val mP = blur(pIn)
        val mIP = blur(FloatArray(I.size) { I[it] * pIn[it] })
        val mII = blur(FloatArray(I.size) { I[it] * I[it] })
        val a = FloatArray(I.size) { (mIP[it] - mI[it] * mP[it]) / (mII[it] - mI[it] * mI[it] + eps) }
        val b = FloatArray(I.size) { mP[it] - a[it] * mI[it] }
        val ma = blur(a)
        val mb = blur(b)
        return FloatArray(I.size) { (ma[it] * I[it] + mb[it]).coerceIn(0f, 1f) }
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    fun sampleBilinear(a: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x = fx.coerceIn(0f, w - 1f)
        val y = fy.coerceIn(0f, h - 1f)
        val x0 = x.toInt().coerceAtMost(w - 1); val y0 = y.toInt().coerceAtMost(h - 1)
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val dx = x - x0; val dy = y - y0
        val top = a[y0 * w + x0] + (a[y0 * w + x1] - a[y0 * w + x0]) * dx
        val bot = a[y1 * w + x0] + (a[y1 * w + x1] - a[y1 * w + x0]) * dx
        return top + (bot - top) * dy
    }

    private fun sampleBilinear3(a: FloatArray, w: Int, h: Int, fx: Float, fy: Float, c: Int): Float {
        val x = fx.coerceIn(0f, w - 1f)
        val y = fy.coerceIn(0f, h - 1f)
        val x0 = x.toInt().coerceAtMost(w - 1); val y0 = y.toInt().coerceAtMost(h - 1)
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val dx = x - x0; val dy = y - y0
        fun v(xx: Int, yy: Int) = a[(yy * w + xx) * 3 + c]
        val top = v(x0, y0) + (v(x1, y0) - v(x0, y0)) * dx
        val bot = v(x0, y1) + (v(x1, y1) - v(x0, y1)) * dx
        return top + (bot - top) * dy
    }

    @Suppress("unused")
    private fun gauss(x: Double, s: Double) = exp(-x * x / (2 * s * s))
}
