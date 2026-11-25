package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.calib.ProcessingModes
import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.Vec3
import com.takehaire.alpha.imaging.look.LookSpec
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.imaging.lut.LutBaker
import com.takehaire.alpha.imaging.process.DevelopParams
import com.takehaire.alpha.imaging.process.RotatingArgbBuffer
import com.takehaire.alpha.imaging.process.StillDeveloper
import com.takehaire.alpha.imaging.raw.FloatSceneSource
import com.takehaire.alpha.imaging.raw.IntRect
import com.takehaire.alpha.imaging.raw.RawSceneSource
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic validation scenes and objective metrics. Every scene here is computer-generated. */
object Validation {

    class ChartResult(val deltaE: List<Double>, val labs: List<DoubleArray>) {
        val mean get() = deltaE.average()
        val max get() = deltaE.max()
    }

    private val toRec2020FromD50 = ColorMath.XYZ_TO_REC2020 * ColorMath.D50_TO_D65

    /** ColorChecker → synthetic RAW capture → our development front end → Lab, ΔE2000 vs reference. */
    fun colorCheckerThroughRaw(cam: SyntheticCamera, illumXy: DoubleArray, iso: Int): ChartResult {
        val ps = 40
        val w = 6 * ps + 16
        val h = 4 * ps + 16
        val illum = ColorMath.xyToXYZ(illumXy[0], illumXy[1])
        val ca = ColorMath.bradford(ColorMath.D50, illum)
        val bg = ca * (ColorMath.D50 * 0.18)
        val scene = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val px = (x - 8) / ps
            val py = (y - 8) / ps
            val v = if (x >= 8 && y >= 8 && px < 6 && py < 4) ca * ColorChecker.xyzD50(py * 6 + px) else bg
            val p = (y * w + x) * 3
            scene[p] = v.x.toFloat(); scene[p + 1] = v.y.toFloat(); scene[p + 2] = v.z.toFloat()
        }
        val raw = cam.capture(scene, w, h, illum, exposure = 0.75f, iso = iso)
        val src = RawSceneSource(raw, 1f)
        val means = (0 until 24).map { i ->
            val px = i % 6
            val py = i / 6
            val r = IntRect(8 + px * ps + 10, 8 + py * ps + 10, ps - 20, ps - 20)
            val buf = FloatArray(r.width * r.height * 3)
            src.fill(r, buf)
            var a = 0.0; var b = 0.0; var c = 0.0
            val n = r.width * r.height
            for (k in 0 until n) { a += buf[k * 3]; b += buf[k * 3 + 1]; c += buf[k * 3 + 2] }
            Vec3(a / n, b / n, c / n)
        }
        val toXyzD50 = ColorMath.D65_TO_D50 * ColorMath.REC2020_TO_XYZ
        val whiteRef = ColorChecker.xyzD50(18).y
        val whiteMeas = (toXyzD50 * means[18]).y
        val labs = means.map { m -> ColorMath.xyzToLab((toXyzD50 * m) * (whiteRef / whiteMeas), ColorMath.D50) }
        val de = labs.indices.map { ColorMath.deltaE2000(labs[it], ColorChecker.LAB_D50[it]) }
        return ChartResult(de, labs)
    }

    // ------------------------------------------------------------------ develop helper
    fun develop(
        scene: FloatArray, w: Int, h: Int, look: LookSpec, mode: ProcessingMode = ProcessingMode.NATURAL,
        iso: Int = 100, whiteEv: Double = 3.0, sigma: Float = 0.002f, lut: Lut3D? = null,
        nrOverride: Pair<Float, Float>? = null, sharpenOverride: Float? = null,
    ): RotatingArgbBuffer {
        val prof = ProcessingModes.profile(mode)
        val cal = Assets.calibration.profiles.first()
        val l = lut ?: LutBaker.bakeScene(look, Assets.alphaNatural, 1.0, whiteEv, size = 33)
        val params = DevelopParams(
            lut = l,
            crop = IntRect(0, 0, w, h),
            nrLuma = nrOverride?.first ?: (cal.at(cal.nrLuma, iso) * prof.nrScale).toFloat().coerceAtMost(1f),
            nrChroma = nrOverride?.second ?: (cal.at(cal.nrChroma, iso) * prof.nrScale).toFloat().coerceAtMost(1f),
            sharpenAmount = sharpenOverride ?: (prof.sharpenAmount * look.sharpening * cal.at(cal.sharpen, iso)).toFloat(),
            grain = if (prof.spatialEffects) look.grain else null,
            halation = if (prof.spatialEffects) look.halation else null,
            vignetteStops = if (prof.spatialEffects) look.vignette.toFloat() else 0f,
            clarity = (look.clarity).toFloat(),
            localTone = prof.localTone.toFloat(),
            threads = 4,
        )
        val sink = RotatingArgbBuffer(w, h, 0)
        StillDeveloper(FloatSceneSource(scene, w, h, sigma), params).develop(sink)
        return sink
    }

    fun luma(px: Int): Double = (0.2126 * (px shr 16 and 0xFF) + 0.7152 * (px shr 8 and 0xFF) + 0.0722 * (px and 0xFF)) / 255.0

    // ------------------------------------------------------------------ metrics
    /** Step edge (optically blurred) → maximum over/undershoot as fraction of the step height. */
    fun edgeOvershoot(look: LookSpec, mode: ProcessingMode): Double {
        val w = 96; val h = 32
        val scene = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            // Optical blur: error-function-like ramp, sigma 0.9 px.
            val t = 1.0 / (1.0 + exp(-(x - 47.5) / 0.55))
            val v = (0.04 + (0.55 - 0.04) * t).toFloat()
            val p = (y * w + x) * 3
            scene[p] = v; scene[p + 1] = v; scene[p + 2] = v
        }
        val out = develop(scene, w, h, look, mode, sigma = 0.0005f)
        val row = (0 until w).map { luma(out.pixels[16 * w + it]) }
        val lo = row.subList(5, 30).average()
        val hi = row.subList(66, 91).average()
        val step = hi - lo
        val over = (row.subList(40, 60).max() - hi) / step
        val under = (lo - row.subList(36, 56).min()) / step
        return max(over, under).coerceAtLeast(0.0)
    }

    private fun noisyScene(w: Int, h: Int, iso: Int, texture: Boolean, seed: Long): Pair<FloatArray, Float> {
        val rnd = Random(seed)
        val sigma = (0.0028 * sqrt(iso / 100.0)).toFloat()
        val s = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val base = if (texture) 0.18 * (1 + 0.15 * sin(2 * PI * x / 4.0)) else 0.18
            val n = sigma * sqrt(base / 0.18 + 0.05)
            val p = (y * w + x) * 3
            val common = rnd.nextGaussian() * n * 0.7
            for (c in 0..2) s[p + c] = (base + common + rnd.nextGaussian() * n * 0.7).toFloat()
        }
        return s to sigma
    }

    /** Amplitude of a 4 px sinusoidal texture after NR relative to no NR (low ISO). */
    fun textureRetention(iso: Int): Double {
        val w = 128; val h = 48
        val (s, sigma) = noisyScene(w, h, iso, texture = true, seed = 5)
        val look = Assets.look("neutral")
        val lut = LutBaker.bakeScene(look, look, 1.0, 3.0, size = 33)
        val with = develop(s, w, h, look, iso = iso, sigma = sigma, lut = lut, sharpenOverride = 0f)
        val without = develop(s, w, h, look, iso = iso, sigma = sigma, lut = lut, nrOverride = 0f to 0f, sharpenOverride = 0f)
        fun amp(b: RotatingArgbBuffer): Double {
            var cs = 0.0; var sn = 0.0; var n = 0
            for (y in 8 until h - 8) for (x in 8 until w - 8) {
                val l = luma(b.pixels[y * w + x])
                cs += l * cos(2 * PI * x / 4.0); sn += l * sin(2 * PI * x / 4.0); n++
            }
            return 2 * hypot(cs, sn) / n
        }
        return amp(with) / amp(without)
    }

    /** Residual noise std after NR relative to no NR on a flat grey (high ISO). */
    fun noiseReduction(iso: Int): Double {
        val w = 96; val h = 64
        val (s, sigma) = noisyScene(w, h, iso, texture = false, seed = 9)
        val look = Assets.look("neutral")
        val lut = LutBaker.bakeScene(look, look, 1.0, 3.0, size = 33)
        fun std(b: RotatingArgbBuffer): Double {
            val v = ArrayList<Double>()
            for (y in 8 until h - 8) for (x in 8 until w - 8) v.add(luma(b.pixels[y * w + x]))
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }
        val a = std(develop(s, w, h, look, iso = iso, sigma = sigma, lut = lut, sharpenOverride = 0f))
        val b = std(develop(s, w, h, look, iso = iso, sigma = sigma, lut = lut, nrOverride = 0f to 0f, sharpenOverride = 0f))
        return a / b
    }

    // ------------------------------------------------------------------ synthetic scenes
    const val SW = 480
    const val SH = 320

    private fun put(s: FloatArray, w: Int, x: Int, y: Int, r: Double, g: Double, b: Double) {
        val p = (y * w + x) * 3
        s[p] = r.toFloat(); s[p + 1] = g.toFloat(); s[p + 2] = b.toFloat()
    }

    private fun srgbToRec(c: DoubleArray): Vec3 = ColorMath.SRGB_TO_REC2020 * Vec3(c[0], c[1], c[2])

    private fun oklchToRec(L: Double, C: Double, h: Double): Vec3 {
        val o = DoubleArray(3)
        ColorMath.oklabToLinearSrgb(L, C * cos(h * PI / 180), C * sin(h * PI / 180), o)
        return ColorMath.SRGB_TO_REC2020 * Vec3(max(o[0], 0.0), max(o[1], 0.0), max(o[2], 0.0))
    }

    /** ColorChecker (D65-lit after white balance), grey ramp −8…+4 EV, skin swatches, hue sweep. */
    fun chartScene(): FloatArray {
        val s = FloatArray(SW * SH * 3)
        for (y in 0 until SH) for (x in 0 until SW) put(s, SW, x, y, 0.09, 0.09, 0.09)
        val ps = 52
        for (i in 0 until 24) {
            val v = toRec2020FromD50 * ColorChecker.xyzD50(i)
            val x0 = 16 + (i % 6) * (ps + 4)
            val y0 = 12 + (i / 6) * (ps + 4)
            for (y in y0 until y0 + ps) for (x in x0 until x0 + ps) put(s, SW, x, y, v.x, v.y, v.z)
        }
        // Skin swatches (Monk scale as reflectance-like values, lit at middle-grey exposure).
        for (i in 0 until 10) {
            val v = srgbToRec(MonkSkinTones.linearSrgb(i)) * 0.85
            val x0 = 370 + (i % 2) * 52
            val y0 = 12 + (i / 2) * 44
            for (y in y0 until y0 + 40) for (x in x0 until x0 + 48) put(s, SW, x, y, v.x, v.y, v.z)
        }
        // Grey ramp, −8 … +4 EV.
        for (y in 248 until 278) for (x in 0 until SW) {
            val ev = -8.0 + 12.0 * x / (SW - 1)
            val v = 0.18 * 2.0.pow(ev)
            put(s, SW, x, y, v, v, v)
        }
        // Hue sweep at moderate chroma, mid lightness.
        for (y in 282 until SH) for (x in 0 until SW) {
            val v = oklchToRec(0.68, 0.11, 360.0 * x / SW) * 1.0
            put(s, SW, x, y, v.x, v.y, v.z)
        }
        return s
    }

    /** Five skin tones as directionally lit spheres (key light from upper left, rim light). */
    fun portraitScene(warm: Boolean = false): FloatArray {
        val s = FloatArray(SW * SH * 3)
        val bgCol = if (warm) Vec3(0.05, 0.035, 0.02) else Vec3(0.03, 0.035, 0.04)
        for (y in 0 until SH) for (x in 0 until SW) {
            val g = 0.6 + 0.4 * y / SH
            put(s, SW, x, y, bgCol.x * g, bgCol.y * g, bgCol.z * g)
        }
        val tones = listOf(1, 3, 5, 7, 9)
        val lx = -0.55; val ly = -0.55; val lz = 0.63
        for ((k, t) in tones.withIndex()) {
            val alb = srgbToRec(MonkSkinTones.linearSrgb(t))
            val cx = 52 + k * 94
            val cy = 150
            val r = 42
            for (y in cy - r until cy + r) for (x in cx - r until cx + r) {
                val nx = (x - cx).toDouble() / r
                val ny = (y - cy).toDouble() / r
                val d2 = nx * nx + ny * ny
                if (d2 > 1) continue
                val nz = sqrt(1 - d2)
                val diffuse = max(0.0, nx * lx + ny * ly + nz * lz)
                val spec = max(0.0, nz * 0.9 + nx * -0.3 + ny * -0.3).pow(40.0) * 2.5
                val rim = max(0.0, nx) .pow(6.0) * 0.6
                val lightCol = if (warm) Vec3(1.0, 0.86, 0.66) else Vec3(1.0, 1.0, 1.0)
                val e = 1.6 * diffuse + 0.12
                val v = Vec3(alb.x * lightCol.x * e + spec + rim, alb.y * lightCol.y * e + spec + rim, alb.z * lightCol.z * e + spec + rim)
                put(s, SW, x, y, v.x, v.y, v.z)
            }
            // White clothing below each sphere in the same light (white in sunlight test).
            for (y in 210 until 300) for (x in cx - 36 until cx + 36) {
                val v = 0.85 * 1.6 * (0.7 + 0.3 * sin(x * 0.7) * sin(y * 0.5))
                put(s, SW, x, y, v, v, v)
            }
        }
        return s
    }

    /** Backlit scene: blue sky gradient, clipped sun, foliage, deep shadows, sunlit white. */
    fun backlitScene(): FloatArray {
        val s = FloatArray(SW * SH * 3)
        for (y in 0 until SH) for (x in 0 until SW) {
            val horizon = 190
            var v: Vec3 = if (y < horizon) {
                val t = y / horizon.toDouble()
                oklchToRec(0.62 + 0.25 * t, 0.10 - 0.06 * t, 245.0) * (1.6 + 1.2 * t)
            } else {
                // Foliage with leaf texture in shadow.
                val n = 0.5 + 0.5 * sin(x * 0.9 + sin(y * 0.6) * 3) * cos(y * 1.3)
                oklchToRec(0.42 + 0.15 * n, 0.09, 128.0 + 10 * n) * (0.25 + 0.25 * n)
            }
            val dx = x - 380.0; val dy = y - 70.0
            val d = sqrt(dx * dx + dy * dy)
            if (d < 18) v = Vec3(40.0, 38.0, 34.0) else v = v + Vec3(1.0, 0.95, 0.85) * (6.0 * exp(-d / 30.0))
            // Silhouetted building edge (deep shadow) with sunlit white wall.
            if (x in 40..150 && y in 60..260) v = if (x < 70) Vec3(0.9 * 3, 0.88 * 3, 0.84 * 3) else Vec3(0.01, 0.011, 0.013)
            put(s, SW, x, y, v.x, v.y, v.z)
        }
        return s
    }

    /** Night street: dark ambience, sodium & LED lights, neon signs, wet reflections. */
    fun nightScene(): FloatArray {
        val s = FloatArray(SW * SH * 3)
        val lights = listOf(
            Triple(80.0, 60.0, Vec3(9.0, 5.0, 1.6)), Triple(240.0, 50.0, Vec3(8.0, 8.0, 7.5)),
            Triple(400.0, 70.0, Vec3(9.0, 5.2, 1.5)), Triple(160.0, 140.0, Vec3(3.0, 0.4, 0.5)),
            Triple(330.0, 130.0, Vec3(0.4, 0.8, 3.5)),
        )
        for (y in 0 until SH) for (x in 0 until SW) {
            var v = Vec3(0.004, 0.005, 0.008) + Vec3(0.01, 0.009, 0.007) * (if (y > 200) 1.0 else 0.4)
            // Building facades.
            if (y in 90..200 && (x / 60) % 2 == 0) v = v + Vec3(0.012, 0.010, 0.008)
            for ((lx, ly, c) in lights) {
                val d = hypot(x - lx, y - ly)
                v = v + c * (if (d < 4) 6.0 else 0.0) + c * (0.06 * exp(-d / 22.0))
                // Wet street reflection.
                if (y > 210) {
                    val dr = hypot((x - lx) * 1.0, (y - (420 - ly)) * 0.25)
                    v = v + c * (0.04 * exp(-dr / 12.0))
                }
            }
            put(s, SW, x, y, v.x, v.y, v.z)
        }
        // A person under a warm light (darker skin tone) — skin at night.
        val alb = srgbToRec(MonkSkinTones.linearSrgb(7))
        for (y in 150 until 200) for (x in 70 until 100) {
            val e = 0.35 * (1 - (y - 150) / 60.0)
            put(s, SW, x, y, alb.x * e * 1.3, alb.y * e, alb.z * e * 0.6)
        }
        return s
    }

    /** Siemens star, fine gratings, step edges and a smooth gradient for banding checks. */
    fun detailScene(): FloatArray {
        val s = FloatArray(SW * SH * 3)
        for (y in 0 until SH) for (x in 0 until SW) {
            var v: Double
            if (x < 220 && y < 220) {
                val a = atan2(y - 110.0, x - 110.0)
                val r = hypot(x - 110.0, y - 110.0)
                v = if (r > 100) 0.18 else if (sin(a * 36) > 0) 0.6 else 0.03
            } else if (x >= 230 && y < 110) {
                val period = 2.0 + (x - 230) / 60.0
                v = 0.18 * (1 + 0.5 * sin(2 * PI * x / period))
            } else if (x >= 230 && y < 220) {
                v = if (((x / 10) + (y / 10)) % 2 == 0) 0.5 else 0.06
                // Fabric weave texture.
                v *= 0.85 + 0.15 * sin(x * 2.1) * sin(y * 1.7)
            } else {
                // Smooth gradient −6 … +2 EV with slight colour (sky-like), banding test.
                val t = x / (SW - 1.0)
                val base = 0.18 * 2.0.pow(-6 + 8 * t)
                val c = oklchToRec(0.7, 0.04, 230.0)
                val k = base / 0.18
                put(s, SW, x, y, c.x * k * 0.45, c.y * k * 0.45, c.z * k * 0.45)
                continue
            }
            put(s, SW, x, y, v, v, v)
        }
        return s
    }

    /** Colorimetric "original": scene encoded straight to sRGB with hard clipping (no rendering). */
    fun colorimetric(scene: FloatArray, w: Int, h: Int): RotatingArgbBuffer {
        val b = RotatingArgbBuffer(w, h, 0)
        val m = ColorMath.REC2020_TO_SRGB.toDoubleArray()
        for (i in 0 until w * h) {
            val r = scene[i * 3].toDouble(); val g = scene[i * 3 + 1].toDouble(); val bl = scene[i * 3 + 2].toDouble()
            val sr = ColorMath.srgbEncode(m[0] * r + m[1] * g + m[2] * bl)
            val sg = ColorMath.srgbEncode(m[3] * r + m[4] * g + m[5] * bl)
            val sb = ColorMath.srgbEncode(m[6] * r + m[7] * g + m[8] * bl)
            b.pixels[i] = (0xFF shl 24) or ((sr * 255 + 0.5).toInt() shl 16) or ((sg * 255 + 0.5).toInt() shl 8) or (sb * 255 + 0.5).toInt()
        }
        return b
    }

    fun minOf3(a: Double, b: Double, c: Double) = min(a, min(b, c))
}
