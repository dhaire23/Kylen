package com.takehaire.alpha.imaging.tone

import com.takehaire.alpha.imaging.color.ColorMath
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow

/**
 * Photographic tone parameters. These are aesthetic controls, not calibrated measurements.
 *
 * @param contrast   multiplies the slope of the curve around middle grey (1 = house contrast)
 * @param midOut     sRGB-encoded output value for scene middle grey (0.18 linear)
 * @param shadowDepth >0 deepens the toe, <0 lifts it (exponent on the shadow segment)
 * @param blackLift  encoded output floor ("fade" of the blacks)
 * @param shoulder   0..1, higher = longer, softer highlight roll-off into white
 * @param whiteClip  encoded output ceiling (matte highlights when < 1)
 */
@Serializable
data class ToneParams(
    val contrast: Double = 1.0,
    val midOut: Double = 0.46,
    val shadowDepth: Double = 0.0,
    val blackLift: Double = 0.0,
    val shoulder: Double = 0.5,
    val whiteClip: Double = 1.0,
)

/**
 * Monotone filmic tone curve operating on scene exposure expressed in stops relative to middle grey.
 *
 * Shadows follow a fixed photographic toe (deeper than a plain sRGB encode, which lifts shadows),
 * highlights follow a Hermite shoulder that reaches white exactly at [whiteEv] — the sensor's clip
 * level relative to middle grey — so clipped sensor data renders white and never grey, while
 * available headroom is used for a smooth roll-off.
 */
class ToneCurve(val params: ToneParams, val whiteEv: Double) {
    private val mid = params.midOut.coerceIn(0.2, 0.7)
    private val c = params.contrast.coerceIn(0.4, 2.5)

    // Base toe: (EV, encoded ratio to mid). Derived from a typical camera rendering intent:
    // ~4.5 stops of visible shadow gradation below middle grey, black reached near -9.5 EV.
    private val toeX = doubleArrayOf(-9.5, -8.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0)
    private val toeY = doubleArrayOf(0.0, 0.006, 0.022, 0.042, 0.075, 0.128, 0.208, 0.322, 0.46)
        .map { it / 0.46 }.toDoubleArray()
    private val toeM: DoubleArray
    private val midSlopeRatio: Double // d(ratio)/dEV at 0

    init {
        toeM = monotoneTangents(toeX, toeY)
        midSlopeRatio = toeM.last()
    }

    /** Slope in encoded units per EV at middle grey. */
    val midSlope: Double get() = mid * midSlopeRatio * c

    fun encoded(ev: Double): Double {
        val raw = if (ev <= 0.0) shadow(ev) else highlight(ev)
        val lifted = params.blackLift + (params.whiteClip - params.blackLift) * raw
        return lifted.coerceIn(0.0, 1.0)
    }

    /** Scene-linear (0.18 = middle grey) → display-linear. */
    fun displayLinear(scene: Double): Double {
        if (scene <= 0.0) return ColorMath.srgbDecode(params.blackLift)
        return ColorMath.srgbDecode(encoded(log2(scene / 0.18)))
    }

    private fun shadow(ev: Double): Double {
        val x = ev * c
        if (x <= toeX[0]) return 0.0
        val ratio = hermiteEval(toeX, toeY, toeM, x)
        // Exponent fades to 1 at middle grey with zero derivative, so the slope stays continuous.
        val r = ratio.coerceIn(0.0, 1.0)
        val shaped = if (params.shadowDepth == 0.0) ratio else r.pow(1.0 + params.shadowDepth * (1 - r) * (1 - r))
        return mid * shaped
    }

    private fun highlight(ev: Double): Double {
        val w = whiteEv.coerceIn(1.5, 10.0)
        if (ev >= w) return 1.0
        val u = ev / w
        val span = 1.0 - mid
        val m0 = (midSlope * w / span).coerceIn(0.05, 3.0)
        // Shoulder: lower end slope → long gentle approach to white.
        val m1 = (0.6 - 0.55 * params.shoulder.coerceIn(0.0, 1.0)).coerceIn(0.02, 3.0)
        val u2 = u * u
        val u3 = u2 * u
        val h10 = u3 - 2 * u2 + u
        val h01 = -2 * u3 + 3 * u2
        val h11 = u3 - u2
        val h = h10 * m0 + h01 + h11 * m1
        return mid + span * h.coerceIn(0.0, 1.0)
    }

    /** Numerical inverse of [encoded] (bisection, curve is monotone). */
    fun inverseEncoded(y: Double): Double {
        var lo = -12.0
        var hi = whiteEv.coerceIn(1.5, 10.0)
        if (y <= encoded(lo)) return lo
        if (y >= encoded(hi)) return hi
        repeat(60) {
            val m = (lo + hi) / 2
            if (encoded(m) < y) lo = m else hi = m
        }
        return (lo + hi) / 2
    }

    companion object {
        fun log2(v: Double) = ln(v) / ln(2.0)

        /** Fritsch–Carlson monotone tangents. */
        fun monotoneTangents(x: DoubleArray, y: DoubleArray): DoubleArray {
            val n = x.size
            val d = DoubleArray(n - 1) { (y[it + 1] - y[it]) / (x[it + 1] - x[it]) }
            val m = DoubleArray(n)
            m[0] = d[0]
            m[n - 1] = d[n - 2]
            for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
            for (i in 0 until n - 1) {
                if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
                val a = m[i] / d[i]
                val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9.0) {
                    val t = 3.0 / kotlin.math.sqrt(s)
                    m[i] = t * a * d[i]
                    m[i + 1] = t * b * d[i]
                }
            }
            return m
        }

        fun hermiteEval(x: DoubleArray, y: DoubleArray, m: DoubleArray, v: Double): Double {
            if (v <= x[0]) return y[0]
            if (v >= x.last()) return y.last()
            var i = 0
            while (i < x.size - 2 && v > x[i + 1]) i++
            val h = x[i + 1] - x[i]
            val t = (v - x[i]) / h
            val t2 = t * t
            val t3 = t2 * t
            return (2 * t3 - 3 * t2 + 1) * y[i] + (t3 - 2 * t2 + t) * h * m[i] +
                (-2 * t3 + 3 * t2) * y[i + 1] + (t3 - t2) * h * m[i + 1]
        }
    }
}
