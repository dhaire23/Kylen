package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.DngColorModel
import com.takehaire.alpha.imaging.color.Mat3
import com.takehaire.alpha.imaging.color.Vec3
import com.takehaire.alpha.imaging.raw.Cfa
import com.takehaire.alpha.imaging.raw.NoiseProfile
import com.takehaire.alpha.imaging.raw.RawFrame
import com.takehaire.alpha.imaging.raw.ShadingMap
import java.util.Random
import kotlin.math.sqrt

/**
 * SYNTHETIC camera used for pipeline-correctness validation. It is NOT a model of any real phone
 * sensor: spectral sensitivities satisfy the Luther condition (camera RGB is a linear transform of
 * XYZ), so a correct DNG-style pipeline must recover scene colorimetry up to noise/demosaic error.
 */
class SyntheticCamera(
    /** Camera RGB = camFromXyz · XYZ. */
    val camFromXyz: Mat3 = DEFAULT_CAM_FROM_XYZ,
    val cfa: Cfa = Cfa.RGGB,
    val black: Float = 64f,
    val white: Float = 1023f,
    val vignetteStrength: Float = 0.45f,
    val useForwardMatrices: Boolean = true,
) {
    /** XYZ (with Y=1 white) of the two calibration illuminants: D65 and Standard A. */
    private val w1 = ColorMath.xyToXYZ(ColorMath.D65_XY[0], ColorMath.D65_XY[1])
    private val aXy = doubleArrayOf(0.44757, 0.40745)
    private val w2 = ColorMath.xyToXYZ(aXy[0], aXy[1])

    val colorModel: DngColorModel by lazy {
        // ColorMatrix (XYZ→camera) normalised the DNG way: maps illuminant white to max component 1.
        fun cm(w: Vec3): Mat3 {
            val n = camFromXyz * w
            return camFromXyz.scale(1.0 / n.maxComponent())
        }
        fun fm(w: Vec3): Mat3 {
            val n = camFromXyz * w
            val ca = ColorMath.bradford(w, ColorMath.D50)
            return ca * camFromXyz.inverse() * Mat3.diag(n)
        }
        DngColorModel(
            colorMatrix1 = cm(w1), colorMatrix2 = cm(w2),
            forwardMatrix1 = if (useForwardMatrices) fm(w1) else null,
            forwardMatrix2 = if (useForwardMatrices) fm(w2) else null,
            illuminant1 = 21, illuminant2 = 17,
        )
    }

    fun shadingGain(u: Float, v: Float): Float {
        val dx = u - 0.5f
        val dy = v - 0.5f
        val r2 = (dx * dx + dy * dy) / 0.5f
        val falloff = 1f / (1f + vignetteStrength * r2 + 0.3f * vignetteStrength * r2 * r2)
        return 1f / falloff
    }

    fun shadingMap(): ShadingMap {
        val cols = 17
        val rows = 13
        val g = FloatArray(cols * rows * 4)
        for (y in 0 until rows) for (x in 0 until cols) {
            val gain = shadingGain(x / (cols - 1f), y / (rows - 1f))
            for (c in 0 until 4) g[(y * cols + x) * 4 + c] = gain
        }
        return ShadingMap(cols, rows, g)
    }

    /**
     * Captures a scene given as XYZ (illuminant-relative, white Y = 1) per pixel.
     * @param exposure multiplier from scene Y to normalised raw signal
     */
    fun capture(
        sceneXyz: FloatArray, width: Int, height: Int, illuminantXyz: Vec3,
        exposure: Float, iso: Int = 100, seed: Long = 7,
    ): RawFrame {
        val rnd = Random(seed)
        val noise = NoiseProfile(DoubleArray(4) { 1.5e-5 * iso / 100.0 }, DoubleArray(4) { 4e-8 * (iso / 100.0) * (iso / 100.0) })
        val data = ShortArray(width * height)
        val m = camFromXyz.toFloatArray()
        for (y in 0 until height) for (x in 0 until width) {
            val p = (y * width + x) * 3
            val X = sceneXyz[p]; val Y = sceneXyz[p + 1]; val Z = sceneXyz[p + 2]
            val c = cfa.colorAt(x, y)
            var v = (m[c * 3] * X + m[c * 3 + 1] * Y + m[c * 3 + 2] * Z) * exposure
            v /= shadingGain(x / (width - 1f), y / (height - 1f))
            val sigma = sqrt((noise.s[0] * v.coerceAtLeast(0f) + noise.o[0]).toFloat())
            v += (rnd.nextGaussian() * sigma).toFloat()
            val dn = (black + v * (white - black)).coerceIn(0f, white)
            data[y * width + x] = (dn + 0.5f).toInt().toShort()
        }
        val neutral = (camFromXyz * illuminantXyz).let { it / it.maxComponent() }
        return RawFrame(
            data, width, height, width, cfa, FloatArray(4) { black }, white, shadingMap(), noise,
            neutral, colorModel, iso, 10_000_000L,
        )
    }

    companion object {
        /** Broad-band but plausible synthetic sensitivities (camera→XYZ inverse of a typical CMOS look). */
        val DEFAULT_CAM_FROM_XYZ: Mat3 = Mat3.of(
            0.68, 0.18, 0.10,
            0.28, 0.82, -0.10,
            0.02, -0.15, 1.10,
        ).inverse()

        /** A second, different synthetic sensor (sensor-to-sensor consistency test). */
        val ALT_CAM_FROM_XYZ: Mat3 = Mat3.of(
            0.62, 0.25, 0.09,
            0.22, 0.90, -0.12,
            0.05, -0.08, 1.02,
        ).inverse()
    }
}

/** X-Rite ColorChecker Classic reference values, CIE L*a*b* D50 (published 2005 data). */
object ColorChecker {
    val NAMES = listOf(
        "dark skin", "light skin", "blue sky", "foliage", "blue flower", "bluish green",
        "orange", "purplish blue", "moderate red", "purple", "yellow green", "orange yellow",
        "blue", "green", "red", "yellow", "magenta", "cyan",
        "white 9.5", "neutral 8", "neutral 6.5", "neutral 5", "neutral 3.5", "black 2",
    )
    val LAB_D50 = arrayOf(
        doubleArrayOf(37.986, 13.555, 14.059), doubleArrayOf(65.711, 18.130, 17.810),
        doubleArrayOf(49.927, -4.880, -21.925), doubleArrayOf(43.139, -13.095, 21.905),
        doubleArrayOf(55.112, 8.844, -25.399), doubleArrayOf(70.719, -33.397, -0.199),
        doubleArrayOf(62.661, 36.067, 57.096), doubleArrayOf(40.020, 10.410, -45.964),
        doubleArrayOf(51.124, 48.239, 16.248), doubleArrayOf(30.325, 22.976, -21.587),
        doubleArrayOf(72.532, -23.709, 57.255), doubleArrayOf(71.941, 19.363, 67.857),
        doubleArrayOf(28.778, 14.179, -50.297), doubleArrayOf(55.261, -38.342, 31.370),
        doubleArrayOf(42.101, 53.378, 28.190), doubleArrayOf(81.733, 4.039, 79.819),
        doubleArrayOf(51.935, 49.986, -14.574), doubleArrayOf(51.038, -28.631, -28.638),
        doubleArrayOf(96.539, -0.425, 1.186), doubleArrayOf(81.257, -0.638, -0.335),
        doubleArrayOf(66.766, -0.734, -0.504), doubleArrayOf(50.867, -0.153, -0.270),
        doubleArrayOf(35.656, -0.421, -1.231), doubleArrayOf(20.461, -0.079, -0.973),
    )

    fun xyzD50(i: Int): Vec3 = ColorMath.labToXyz(LAB_D50[i], ColorMath.D50)
}

/**
 * Monk Skin Tone Scale (Google, CC BY 4.0) — ten reference swatches spanning light to deep skin.
 * Used as display-referred appearance targets for skin rendering checks.
 */
object MonkSkinTones {
    val HEX = listOf("f6ede4", "f3e7db", "f7ead0", "eadaba", "d7bd96", "a07e56", "825c43", "604134", "3a312a", "292420")

    fun linearSrgb(i: Int): DoubleArray {
        val v = HEX[i].toInt(16)
        return doubleArrayOf(
            ColorMath.srgbDecode(((v shr 16) and 0xFF) / 255.0),
            ColorMath.srgbDecode(((v shr 8) and 0xFF) / 255.0),
            ColorMath.srgbDecode((v and 0xFF) / 255.0),
        )
    }
}
