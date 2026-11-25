package com.takehaire.alpha.imaging.lut

import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.look.LookRenderer
import com.takehaire.alpha.imaging.look.LookSpec
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow

/**
 * Bakes looks into 3D LUTs.
 *
 *  - Scene LUT (still pipeline): input is scene-linear Rec.2020 encoded with [SceneLog]; 65³.
 *  - Preview LUT (GPU): input is the camera's processed preview value (sRGB-encoded), which is
 *    decoded, exposure-normalised and passed through the very same [LookRenderer]; 33³.
 *
 * Intensity blends the look with the house ALPHA NATURAL rendering in display-linear light.
 */
object LutBaker {

    /** Log encoding used for the scene-referred LUT domain: −11 … +5.5 EV around middle grey (the toe reaches black near −9.5 EV). */
    object SceneLog {
        const val MIN_EV = -11.0
        const val MAX_EV = 5.5
        private const val RANGE = MAX_EV - MIN_EV
        private const val INV_LN2 = 1.4426950408889634
        private const val FLOOR = 0.18f * 4.8828125E-4f // 0.18 * 2^-11

        fun encode(v: Float): Float {
            val x = if (v < FLOOR) FLOOR else v
            val ev = ln((x / 0.18f).toDouble()) * INV_LN2
            return ((ev - MIN_EV) / RANGE).toFloat().coerceIn(0f, 1f)
        }

        fun decode(e: Double): Double = 0.18 * 2.0.pow(MIN_EV + e * RANGE)
    }

    data class SourceModel(
        /** Exposure multiplier applied after decoding (preview/YUV normalisation). */
        val exposureScale: Double = 1.0,
        /** True when the camera vendor tone curve was replaced by the sRGB preset (verified via results). */
        val vendorCurveIsSrgb: Boolean = true,
    )

    fun bakeScene(
        look: LookSpec,
        base: LookSpec,
        intensity: Double,
        whiteEv: Double,
        size: Int = 65,
        post: Lut3D? = null,
    ): Lut3D = bake(size, "${look.id}-scene") { renderers ->
        val (lr, br) = renderers
        val tmp = DoubleArray(3)
        val tmpB = DoubleArray(3)
        val pf = FloatArray(3)
        return@bake { e: DoubleArray, out: FloatArray ->
            val r = SceneLog.decode(e[0])
            val g = SceneLog.decode(e[1])
            val b = SceneLog.decode(e[2])
            blendRender(lr, br, intensity, r, g, b, tmp, tmpB)
            applyPost(post, tmp, pf)
            out[0] = tmp[0].toFloat(); out[1] = tmp[1].toFloat(); out[2] = tmp[2].toFloat()
        }
    }.let { it(look, base, whiteEv, false) }

    fun bakePreview(
        look: LookSpec,
        base: LookSpec,
        intensity: Double,
        source: SourceModel,
        size: Int = 33,
        post: Lut3D? = null,
    ): Lut3D {
        val whiteEv = log2(1.0 / 0.18 * source.exposureScale)
        val m = ColorMath.SRGB_TO_REC2020.toDoubleArray()
        return bake(size, "${look.id}-preview") { renderers ->
            val (lr, br) = renderers
            val tmp = DoubleArray(3)
            val tmpB = DoubleArray(3)
            val pf = FloatArray(3)
            return@bake { e: DoubleArray, out: FloatArray ->
                val lr0 = ColorMath.srgbDecode(e[0]) * source.exposureScale
                val lg0 = ColorMath.srgbDecode(e[1]) * source.exposureScale
                val lb0 = ColorMath.srgbDecode(e[2]) * source.exposureScale
                val r = m[0] * lr0 + m[1] * lg0 + m[2] * lb0
                val g = m[3] * lr0 + m[4] * lg0 + m[5] * lb0
                val b = m[6] * lr0 + m[7] * lg0 + m[8] * lb0
                blendRender(lr, br, intensity, r, g, b, tmp, tmpB)
                applyPost(post, tmp, pf)
                out[0] = tmp[0].toFloat(); out[1] = tmp[1].toFloat(); out[2] = tmp[2].toFloat()
            }
        }.let { it(look, base, whiteEv, !source.vendorCurveIsSrgb) }
    }

    private fun applyPost(post: Lut3D?, v: DoubleArray, scratch: FloatArray) {
        if (post == null) return
        post.lookup(v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), scratch)
        v[0] = scratch[0].toDouble(); v[1] = scratch[1].toDouble(); v[2] = scratch[2].toDouble()
    }

    private fun blendRender(
        lr: LookRenderer,
        br: LookRenderer?,
        intensity: Double,
        r: Double, g: Double, b: Double,
        out: DoubleArray,
        tmpB: DoubleArray,
    ) {
        lr.render(r, g, b, out)
        if (br == null || intensity >= 0.999) return
        br.render(r, g, b, tmpB)
        for (i in 0..2) {
            val a = ColorMath.srgbDecode(tmpB[i])
            val l = ColorMath.srgbDecode(out[i])
            out[i] = ColorMath.srgbEncode(a + (l - a) * intensity)
        }
    }

    private fun log2(v: Double) = ln(v) / ln(2.0)

    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    private fun bake(
        size: Int,
        title: String,
        kernelFactory: (Pair<LookRenderer, LookRenderer?>) -> (DoubleArray, FloatArray) -> Unit,
    ): (LookSpec, LookSpec, Double, Boolean) -> Lut3D = { look, base, whiteEv, displayRef ->
        val data = FloatArray(size * size * size * 3)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = (0 until threads).map { t ->
                Callable {
                    val lr = LookRenderer(look, whiteEv, displayRef)
                    val br = if (base.id == look.id) null else LookRenderer(base, whiteEv, displayRef)
                    val kernel = kernelFactory(lr to br)
                    val e = DoubleArray(3)
                    val o = FloatArray(3)
                    var bIdx = t
                    while (bIdx < size) {
                        for (g in 0 until size) for (r in 0 until size) {
                            e[0] = r / (size - 1.0); e[1] = g / (size - 1.0); e[2] = bIdx / (size - 1.0)
                            kernel(e, o)
                            val i = ((bIdx * size + g) * size + r) * 3
                            data[i] = o[0]; data[i + 1] = o[1]; data[i + 2] = o[2]
                        }
                        bIdx += threads
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        Lut3D(size, data, title)
    }
}
