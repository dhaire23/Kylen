package com.takehaire.alpha.imaging.raw

import com.takehaire.alpha.imaging.color.DngColorModel
import com.takehaire.alpha.imaging.color.Vec3

/** Colour filter arrangement (Camera2 SENSOR_INFO_COLOR_FILTER_ARRANGEMENT 0..3). */
enum class Cfa(val pattern: IntArray) {
    // pattern[(y&1)*2 + (x&1)] = 0:R 1:G 2:B
    RGGB(intArrayOf(0, 1, 1, 2)),
    GRBG(intArrayOf(1, 0, 2, 1)),
    GBRG(intArrayOf(1, 2, 0, 1)),
    BGGR(intArrayOf(2, 1, 1, 0));

    fun colorAt(x: Int, y: Int): Int = pattern[((y and 1) shl 1) or (x and 1)]

    companion object {
        fun fromCamera2(value: Int): Cfa? = entries.getOrNull(value)
    }
}

/**
 * Lens shading gain map as delivered by Camera2 (STATISTICS_LENS_SHADING_CORRECTION_MAP).
 * gains holds 4 floats per cell in Camera2 order: R, G_even, G_odd, B. The map spans the full frame.
 */
class ShadingMap(val columns: Int, val rows: Int, val gains: FloatArray) {
    init {
        require(columns >= 2 && rows >= 2 && gains.size == columns * rows * 4)
    }

    /** Bilinear gain for channel (0 R, 1 G_even, 2 G_odd, 3 B) at normalised position u,v in [0,1]. */
    fun gain(channel: Int, u: Float, v: Float): Float {
        val fx = (u * (columns - 1)).coerceIn(0f, (columns - 1).toFloat())
        val fy = (v * (rows - 1)).coerceIn(0f, (rows - 1).toFloat())
        val x0 = fx.toInt().coerceAtMost(columns - 2)
        val y0 = fy.toInt().coerceAtMost(rows - 2)
        val dx = fx - x0
        val dy = fy - y0
        fun g(x: Int, y: Int) = gains[(y * columns + x) * 4 + channel]
        val top = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * dx
        val bot = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * dx
        return top + (bot - top) * dy
    }
}

/** Camera2 SENSOR_NOISE_PROFILE: variance = s * x + o for normalised x, per CFA channel. */
class NoiseProfile(val s: DoubleArray, val o: DoubleArray) {
    val meanS = s.average()
    val meanO = o.average()

    /** Standard deviation (normalised raw units) at normalised signal level x. */
    fun sigma(x: Double): Double = kotlin.math.sqrt((meanS * x.coerceAtLeast(0.0) + meanO).coerceAtLeast(1e-12))

    companion object {
        /**
         * Conservative model used only when the HAL provides no noise profile: shot+read noise scaled
         * with ISO relative to a small-pixel sensor. Flagged as an estimate in diagnostics.
         */
        fun estimate(iso: Int): NoiseProfile {
            val g = iso / 100.0
            return NoiseProfile(DoubleArray(4) { 2.0e-5 * g }, DoubleArray(4) { 6.0e-8 * g * g })
        }
    }
}

/**
 * One RAW_SENSOR frame with all metadata needed for development.
 *
 * @param data 16-bit samples, row stride [rowStride] in samples
 * @param blackLevel per 2x2 position, row-major ((y&1)*2 + (x&1))
 * @param neutral as-shot camera neutral (SENSOR_NEUTRAL_COLOR_POINT) or a manual WB neutral
 */
class RawFrame(
    val data: ShortArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val cfa: Cfa,
    val blackLevel: FloatArray,
    val whiteLevel: Float,
    val shading: ShadingMap?,
    val noise: NoiseProfile,
    val neutral: Vec3,
    val colorModel: DngColorModel,
    val iso: Int,
    val exposureTimeNs: Long,
) {
    init {
        require(blackLevel.size == 4)
        require(data.size >= rowStride * (height - 1) + width)
        require(whiteLevel > blackLevel.max())
    }

    fun sample(x: Int, y: Int): Int = data[y * rowStride + x].toInt() and 0xFFFF
}
