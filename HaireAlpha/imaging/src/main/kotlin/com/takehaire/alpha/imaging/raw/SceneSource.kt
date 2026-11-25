package com.takehaire.alpha.imaging.raw

import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.Mat3
import com.takehaire.alpha.imaging.color.Vec3

/** Integer rectangle in source pixel coordinates. */
data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
}

/** Low-resolution scene-linear Rec.2020 proxy of the crop region (analysis, halation, local tone). */
class ProxyImage(val width: Int, val height: Int, val factor: Int, val origin: IntRect, val rgb: FloatArray) {
    fun luminance(i: Int): Float =
        (0.2627f * rgb[i * 3] + 0.6780f * rgb[i * 3 + 1] + 0.0593f * rgb[i * 3 + 2])
}

/**
 * Produces scene-linear Rec.2020 RGB (0.18 = middle grey after exposure normalisation) for any
 * rectangle of the source, mirroring at the borders. Implementations must be thread-safe for
 * concurrent [fill] calls on different rectangles.
 */
interface SceneSource {
    val width: Int
    val height: Int
    val description: String

    /** Scene-linear noise standard deviation at scene level y (for adaptive NR/sharpening). */
    fun noiseSigma(y: Float): Float

    fun fill(rect: IntRect, out: FloatArray)

    fun proxy(crop: IntRect, factor: Int): ProxyImage
}

internal fun mirror(v: Int, n: Int): Int {
    var x = v
    if (x < 0) x = -x
    if (x >= n) x = 2 * (n - 1) - x
    return x.coerceIn(0, n - 1)
}

/**
 * RAW development front end: black level, white level, lens shading, white balance, highlight
 * clipping, defect suppression, Malvar–He–Cutler demosaic and the DNG camera → Rec.2020 matrix.
 */
class RawSceneSource(
    val frame: RawFrame,
    /** Multiplier mapping white-balanced normalised raw to scene (0.18 = middle grey). */
    val exposureScale: Float,
) : SceneSource {
    override val width = frame.width
    override val height = frame.height
    override val description = "RAW_SENSOR ${frame.width}x${frame.height} ${frame.cfa}"

    /** White-balance gains (1 / neutral, normalised so the smallest gain is 1). */
    val wbGains: FloatArray
    /** Camera (white balanced) → linear Rec.2020, including exposure scale. */
    val matrix: FloatArray
    val cameraToRec2020: Mat3

    init {
        val n = frame.neutral
        val maxN = n.maxComponent()
        wbGains = floatArrayOf((maxN / n.x).toFloat(), (maxN / n.y).toFloat(), (maxN / n.z).toFloat())
        cameraToRec2020 = frame.colorModel.cameraToRec2020(n)
        // WB is applied before demosaic, so fold its inverse into the matrix.
        val undoWb = Mat3.diag(1.0 / wbGains[0], 1.0 / wbGains[1], 1.0 / wbGains[2])
        matrix = (cameraToRec2020 * undoWb).scale(exposureScale.toDouble()).toFloatArray()
    }

    private val scale = FloatArray(4) { 1f / (frame.whiteLevel - frame.blackLevel[it]) }

    override fun noiseSigma(y: Float): Float {
        // y is scene level; convert back to normalised raw green, then forward again.
        val raw = y / exposureScale
        return (frame.noise.sigma(raw.toDouble()) * exposureScale).toFloat()
    }

    /** Normalised, shading-corrected, white-balanced CFA sample at mirrored coordinates. */
    private fun cfaValue(x: Int, y: Int): Float {
        val xm = mirror(x, width)
        val ym = mirror(y, height)
        val pos = ((ym and 1) shl 1) or (xm and 1)
        val c = frame.cfa.pattern[pos]
        var v = (frame.sample(xm, ym) - frame.blackLevel[pos]) * scale[pos]
        if (v < 0f) v = 0f
        val sh = frame.shading
        var clip = 1f
        if (sh != null) {
            val u = xm / (width - 1f)
            val w = ym / (height - 1f)
            val ch = when (c) { 0 -> 0; 2 -> 3; else -> if ((ym and 1) == 0) 1 else 2 }
            v *= sh.gain(ch, u, w)
            clip = sh.gain(if ((ym and 1) == 0) 1 else 2, u, w)
        }
        v *= wbGains[c]
        // Clip every channel at the level where the least-amplified channel saturates, so blown
        // highlights render neutral instead of magenta (no reconstruction is claimed).
        return if (v > clip) clip else v
    }

    override fun fill(rect: IntRect, out: FloatArray) {
        val m = 2
        val mw = rect.width + 2 * m
        val mh = rect.height + 2 * m
        val mos = FloatArray(mw * mh)
        for (j in 0 until mh) {
            val y = rect.y - m + j
            for (i in 0 until mw) mos[j * mw + i] = cfaValue(rect.x - m + i, y)
        }
        suppressDefects(mos, mw, mh, rect.x - m, rect.y - m)
        demosaicMhc(mos, mw, mh, rect.x - m, rect.y - m, m, rect.width, rect.height, out)
        val mt = matrix
        var i = 0
        val n = rect.width * rect.height * 3
        while (i < n) {
            val r = out[i]; val g = out[i + 1]; val b = out[i + 2]
            out[i] = mt[0] * r + mt[1] * g + mt[2] * b
            out[i + 1] = mt[3] * r + mt[4] * g + mt[5] * b
            out[i + 2] = mt[6] * r + mt[7] * g + mt[8] * b
            i += 3
        }
    }

    /** Replaces isolated hot pixels that exceed every same-colour neighbour by a wide margin. */
    private fun suppressDefects(mos: FloatArray, mw: Int, mh: Int, ox: Int, oy: Int) {
        for (j in 2 until mh - 2) for (i in 2 until mw - 2) {
            val v = mos[j * mw + i]
            if (v < 0.02f) continue
            val a = mos[(j - 2) * mw + i]
            val b = mos[(j + 2) * mw + i]
            val c = mos[j * mw + i - 2]
            val d = mos[j * mw + i + 2]
            val mx = maxOf(maxOf(a, b), maxOf(c, d))
            if (v > 4f * mx + 0.02f) mos[j * mw + i] = mx
        }
    }

    /**
     * Malvar, He, Cutler (2004) "High-quality linear interpolation for demosaicing of Bayer-patterned
     * color images". Writes RGB for the inner rect (offset m) into out.
     */
    private fun demosaicMhc(
        mos: FloatArray, mw: Int, mh: Int, ox: Int, oy: Int, m: Int,
        w: Int, h: Int, out: FloatArray,
    ) {
        val cfa = frame.cfa
        for (j in 0 until h) {
            val my = j + m
            val ay = oy + my
            val row = my * mw
            for (i in 0 until w) {
                val mx = i + m
                val ax = ox + mx
                val p = row + mx
                val c = cfa.colorAt(ax, ay)
                val v = mos[p]
                val n = mos[p - mw]; val s = mos[p + mw]; val e = mos[p + 1]; val wv = mos[p - 1]
                val nn = mos[p - 2 * mw]; val ss = mos[p + 2 * mw]; val ee = mos[p + 2]; val ww = mos[p - 2]
                val ne = mos[p - mw + 1]; val nw = mos[p - mw - 1]; val se = mos[p + mw + 1]; val sw = mos[p + mw - 1]
                var r: Float
                var g: Float
                var b: Float
                when (c) {
                    0, 2 -> {
                        val cross = n + s + e + wv
                        val far = nn + ss + ee + ww
                        val diag = ne + nw + se + sw
                        val gg = (4f * v + 2f * cross - far) * 0.125f
                        val other = (6f * v + 2f * diag - 1.5f * far) * 0.125f
                        g = gg
                        if (c == 0) { r = v; b = other } else { b = v; r = other }
                    }
                    else -> {
                        g = v
                        val diag = ne + nw + se + sw
                        // Horizontal neighbours' colour:
                        val hColor = cfa.colorAt(ax + 1, ay)
                        val horiz = (5f * v + 4f * (e + wv) - (ee + ww) - diag + 0.5f * (nn + ss)) * 0.125f
                        val vert = (5f * v + 4f * (n + s) - (nn + ss) - diag + 0.5f * (ee + ww)) * 0.125f
                        if (hColor == 0) { r = horiz; b = vert } else { b = horiz; r = vert }
                    }
                }
                val o = (j * w + i) * 3
                out[o] = if (r < 0f) 0f else r
                out[o + 1] = if (g < 0f) 0f else g
                out[o + 2] = if (b < 0f) 0f else b
            }
        }
    }

    override fun proxy(crop: IntRect, factor: Int): ProxyImage {
        val pw = (crop.width / factor).coerceAtLeast(1)
        val ph = (crop.height / factor).coerceAtLeast(1)
        val out = FloatArray(pw * ph * 3)
        val mt = matrix
        val acc = FloatArray(3)
        val cnt = IntArray(3)
        for (py in 0 until ph) for (px in 0 until pw) {
            acc.fill(0f); cnt.fill(0)
            val bx = crop.x + px * factor
            val by = crop.y + py * factor
            // Sample a few 2x2 quads in the block (enough for low-frequency statistics).
            val step = (factor / 2).coerceAtLeast(2) and 0x7FFFFFFE
            var yy = 0
            while (yy < factor) {
                var xx = 0
                while (xx < factor) {
                    val x0 = (bx + xx) and 0x7FFFFFFE
                    val y0 = (by + yy) and 0x7FFFFFFE
                    for (q in 0 until 4) {
                        val x = x0 + (q and 1)
                        val y = y0 + (q shr 1)
                        val c = frame.cfa.colorAt(mirror(x, width), mirror(y, height))
                        acc[c] += cfaValue(x, y); cnt[c]++
                    }
                    xx += step
                }
                yy += step
            }
            val r = acc[0] / cnt[0].coerceAtLeast(1)
            val g = acc[1] / cnt[1].coerceAtLeast(1)
            val b = acc[2] / cnt[2].coerceAtLeast(1)
            val o = (py * pw + px) * 3
            out[o] = mt[0] * r + mt[1] * g + mt[2] * b
            out[o + 1] = mt[3] * r + mt[4] * g + mt[5] * b
            out[o + 2] = mt[6] * r + mt[7] * g + mt[8] * b
        }
        return ProxyImage(pw, ph, factor, crop, out)
    }
}

/**
 * Processed-image front end (YUV_420_888 converted to RGB, or decoded vendor JPEG). The 8-bit
 * values are decoded with the sRGB EOTF, which is exact when the capture used the sRGB preset tone
 * curve and an approximation otherwise (the renderer is told via displayReferred mode).
 *
 * @param argb packed 0xAARRGGBB pixels, row stride = width
 */
class RgbSceneSource(
    private val argb: IntArray,
    override val width: Int,
    override val height: Int,
    val exposureScale: Float,
    private val iso: Int,
    override val description: String = "Processed RGB ${width}x$height",
) : SceneSource {
    private val decode = FloatArray(256) { (ColorMath.srgbDecode(it / 255.0) * exposureScale).toFloat() }
    private val m = ColorMath.SRGB_TO_REC2020.toFloatArray()

    override fun noiseSigma(y: Float): Float {
        // Vendor NR already ran; model residual noise plus 8-bit quantisation in linear light.
        val g = iso / 100f
        val shot = 0.0025f * g * kotlin.math.sqrt(y.coerceAtLeast(0f) + 0.002f)
        val quant = 0.0015f * (y.coerceAtLeast(0.001f)) // one 8-bit step in encoded ≈ y * 0.5%..1.5%
        return shot + quant
    }

    override fun fill(rect: IntRect, out: FloatArray) {
        var o = 0
        for (j in 0 until rect.height) {
            val y = mirror(rect.y + j, height)
            val row = y * width
            for (i in 0 until rect.width) {
                val x = mirror(rect.x + i, width)
                val p = argb[row + x]
                val r = decode[(p shr 16) and 0xFF]
                val g = decode[(p shr 8) and 0xFF]
                val b = decode[p and 0xFF]
                out[o] = m[0] * r + m[1] * g + m[2] * b
                out[o + 1] = m[3] * r + m[4] * g + m[5] * b
                out[o + 2] = m[6] * r + m[7] * g + m[8] * b
                o += 3
            }
        }
    }

    override fun proxy(crop: IntRect, factor: Int): ProxyImage {
        val pw = (crop.width / factor).coerceAtLeast(1)
        val ph = (crop.height / factor).coerceAtLeast(1)
        val out = FloatArray(pw * ph * 3)
        val tmp = FloatArray(3)
        val step = (factor / 3).coerceAtLeast(1)
        for (py in 0 until ph) for (px in 0 until pw) {
            var r = 0f; var g = 0f; var b = 0f; var n = 0
            var yy = 0
            while (yy < factor) {
                var xx = 0
                while (xx < factor) {
                    fill(IntRect(crop.x + px * factor + xx, crop.y + py * factor + yy, 1, 1), tmp)
                    r += tmp[0]; g += tmp[1]; b += tmp[2]; n++
                    xx += step
                }
                yy += step
            }
            val o = (py * pw + px) * 3
            out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
        }
        return ProxyImage(pw, ph, factor, crop, out)
    }
}

/** Float RGB scene image (tests and synthetic validation). */
class FloatSceneSource(
    private val rgb: FloatArray,
    override val width: Int,
    override val height: Int,
    private val sigma: Float = 0.002f,
    override val description: String = "Float scene",
) : SceneSource {
    override fun noiseSigma(y: Float) = sigma * kotlin.math.sqrt(y.coerceAtLeast(0f) / 0.18f + 0.05f)
    override fun fill(rect: IntRect, out: FloatArray) {
        var o = 0
        for (j in 0 until rect.height) {
            val y = mirror(rect.y + j, height)
            for (i in 0 until rect.width) {
                val x = mirror(rect.x + i, width)
                val p = (y * width + x) * 3
                out[o] = rgb[p]; out[o + 1] = rgb[p + 1]; out[o + 2] = rgb[p + 2]
                o += 3
            }
        }
    }

    override fun proxy(crop: IntRect, factor: Int): ProxyImage {
        val pw = (crop.width / factor).coerceAtLeast(1)
        val ph = (crop.height / factor).coerceAtLeast(1)
        val out = FloatArray(pw * ph * 3)
        for (py in 0 until ph) for (px in 0 until pw) {
            var r = 0f; var g = 0f; var b = 0f; var n = 0
            for (yy in 0 until factor) for (xx in 0 until factor) {
                val x = mirror(crop.x + px * factor + xx, width)
                val y = mirror(crop.y + py * factor + yy, height)
                val p = (y * width + x) * 3
                r += rgb[p]; g += rgb[p + 1]; b += rgb[p + 2]; n++
            }
            val o = (py * pw + px) * 3
            out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
        }
        return ProxyImage(pw, ph, factor, crop, out)
    }
}

/** Utility: camera neutral of a white-balance gains vector (gains = 1/neutral). */
fun neutralFromGains(r: Float, g: Float, b: Float): Vec3 {
    val n = Vec3(1.0 / r, 1.0 / g, 1.0 / b)
    return n / n.maxComponent()
}
