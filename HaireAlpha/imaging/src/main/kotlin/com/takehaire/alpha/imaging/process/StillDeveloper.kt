package com.takehaire.alpha.imaging.process

import com.takehaire.alpha.imaging.look.GrainSpec
import com.takehaire.alpha.imaging.look.HalationSpec
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.imaging.lut.LutBaker
import com.takehaire.alpha.imaging.raw.IntRect
import com.takehaire.alpha.imaging.raw.ProxyImage
import com.takehaire.alpha.imaging.raw.SceneSource
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Receives finished 0xAARRGGBB stripes in source orientation (rows of the crop rectangle). */
fun interface StripeSink {
    fun write(cropRowStart: Int, width: Int, rows: Int, argb: IntArray)
}

/**
 * Spatial processing parameters for one capture. Colour is entirely in [lut].
 */
data class DevelopParams(
    val lut: Lut3D,
    val crop: IntRect,
    /** 0..1 luminance noise reduction strength (applied relative to the measured noise model). */
    val nrLuma: Float = 0.3f,
    /** 0..1 chroma noise reduction strength. */
    val nrChroma: Float = 0.6f,
    /** Unsharp amount on display luminance (0.35 = restrained). */
    val sharpenAmount: Float = 0.35f,
    /** Halo clamp: allowed overshoot beyond the local 3x3 min/max, encoded units. */
    val haloLimit: Float = 0.012f,
    val grain: GrainSpec? = null,
    val halation: HalationSpec? = null,
    /** Corner darkening in stops (lens-like cos^4-shaped falloff). */
    val vignetteStops: Float = 0f,
    /** Local contrast (clarity), log-domain, typically −0.3..0.3. */
    val clarity: Float = 0f,
    /** Local tone compression strength for COMPUTATIONAL mode (0 = off). */
    val localTone: Float = 0f,
    val seed: Int = 1,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    val stripeRows: Int = 64,
)

class DevelopResult(val proxy: ProxyImage, val millis: Long, val clippedFraction: Float)

/**
 * Striped, multi-threaded still development: scene source → noise reduction (linear, noise-model
 * driven) → halation/vignette/local tone → 3D LUT (all colour) → halo-clamped sharpening on display
 * luminance → resolution-scaled grain → sink. Peak memory is a few MB per thread regardless of
 * image size; the output never has to exist twice in memory.
 */
class StillDeveloper(private val source: SceneSource, private val params: DevelopParams) {

    private val crop = params.crop
    private val shortSide = min(crop.width, crop.height)
    private lateinit var proxy: ProxyImage
    private var halationMap: FloatArray? = null
    private var lowLog: FloatArray? = null

    fun develop(sink: StripeSink, cancelled: AtomicBoolean = AtomicBoolean(false)): DevelopResult {
        val t0 = System.nanoTime()
        val factor = (shortSide / 360).coerceIn(1, 16)
        proxy = source.proxy(crop, factor)
        prepareProxyMaps()

        val pool = Executors.newFixedThreadPool(params.threads)
        val stripes = (crop.height + params.stripeRows - 1) / params.stripeRows
        var clipped = 0L
        try {
            val tasks = (0 until params.threads).map { t ->
                Callable {
                    val worker = Worker()
                    var s = t
                    var localClip = 0L
                    while (s < stripes) {
                        if (cancelled.get()) break
                        val r0 = s * params.stripeRows
                        val rows = min(params.stripeRows, crop.height - r0)
                        localClip += worker.process(r0, rows, sink)
                        s += params.threads
                    }
                    localClip
                }
            }
            for (f in pool.invokeAll(tasks)) clipped += f.get()
        } finally {
            pool.shutdown()
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        return DevelopResult(proxy, ms, clipped.toFloat() / (crop.width.toLong() * crop.height).toFloat())
    }

    // ------------------------------------------------------------------ proxy-derived maps
    private fun prepareProxyMaps() {
        val p = proxy
        val n = p.width * p.height
        val hal = params.halation
        if (hal != null && hal.amount > 0.0) {
            val m = FloatArray(n) { i ->
                val y = p.luminance(i)
                val thr = (0.18 * Math.pow(2.0, hal.threshold)).toFloat()
                if (y > thr) min(y - thr, 8f) else 0f
            }
            val radius = (hal.radius * shortSide / p.factor).toFloat().coerceIn(1f, 64f)
            gaussianBlur(m, p.width, p.height, radius)
            halationMap = m
        }
        if (params.clarity != 0f || params.localTone != 0f) {
            val l = FloatArray(n) { i -> log2(max(p.luminance(i), 1e-5f)) }
            val radius = (0.025f * shortSide / p.factor).coerceIn(2f, 64f)
            // Edge-preserving low-pass (self-guided filter, He et al. 2010): large tonal edges survive,
            // so local tone and clarity do not paint halos along them.
            lowLog = guidedFilter(l, p.width, p.height, radius, 0.25f)
        }
    }

    private fun sampleProxyMap(map: FloatArray, cx: Int, cy: Int): Float {
        val p = proxy
        val fx = ((cx + 0.5f) / p.factor - 0.5f).coerceIn(0f, p.width - 1f)
        val fy = ((cy + 0.5f) / p.factor - 0.5f).coerceIn(0f, p.height - 1f)
        val x0 = fx.toInt().coerceAtMost(p.width - 2).coerceAtLeast(0)
        val y0 = fy.toInt().coerceAtMost(p.height - 2).coerceAtLeast(0)
        val x1 = min(x0 + 1, p.width - 1)
        val y1 = min(y0 + 1, p.height - 1)
        val dx = fx - x0
        val dy = fy - y0
        val a = map[y0 * p.width + x0]
        val b = map[y0 * p.width + x1]
        val c = map[y1 * p.width + x0]
        val d = map[y1 * p.width + x1]
        val top = a + (b - a) * dx
        val bot = c + (d - c) * dx
        return top + (bot - top) * dy
    }

    // ------------------------------------------------------------------ per-thread worker
    private inner class Worker {
        private val margin = 4 // NR radius 4 (chroma, stride 2) + sharpen 1 handled below
        private var scene = FloatArray(0)
        private var nr = FloatArray(0)
        private var disp = FloatArray(0)
        private var outArgb = IntArray(0)
        private val lutOut = FloatArray(3)

        private val halTint: FloatArray = params.halation?.let { h ->
            val rad = Math.toRadians(h.hue)
            // Warm glow colour in Rec.2020 linear (red-dominant), luminance-normalised.
            val r = 1.0f
            val g = (0.30 + 0.25 * sin(rad)).toFloat().coerceIn(0.05f, 0.6f)
            val b = (0.08 + 0.05 * cos(rad)).toFloat().coerceIn(0.02f, 0.3f)
            val y = 0.2627f * r + 0.6780f * g + 0.0593f * b
            floatArrayOf(r / y, g / y, b / y)
        } ?: floatArrayOf(0f, 0f, 0f)

        /** Returns the number of clipped output pixels in this stripe. */
        fun process(r0: Int, rows: Int, sink: StripeSink): Long {
            val w = crop.width
            // Region needed: NR needs ±4 (chroma), sharpening ±1 on top.
            val m = margin + 1
            val sw = w + 2 * m
            val sh = rows + 2 * m
            ensure(sw * sh)
            val rect = IntRect(crop.x - m, crop.y + r0 - m, sw, sh)
            source.fill(rect, scene)
            spatialScene(rect, sw, sh)
            noiseReduce(sw, sh)
            // Display rows: inner region expanded by 1 for sharpening.
            val dw = w + 2
            val dh = rows + 2
            lutPass(sw, dw, dh, m - 1)
            return sharpenGrainPack(r0, w, rows, dw, sink)
        }

        private fun ensure(n: Int) {
            if (scene.size < n * 3) {
                scene = FloatArray(n * 3)
                nr = FloatArray(n * 3)
                disp = FloatArray(n * 3)
                outArgb = IntArray(n)
            }
        }

        /** Vignette, halation and local tone on scene-linear data. */
        private fun spatialScene(rect: IntRect, sw: Int, sh: Int) {
            val vig = params.vignetteStops
            val hal = halationMap
            val low = lowLog
            if (vig == 0f && hal == null && low == null) return
            val halAmt = (params.halation?.amount ?: 0.0).toFloat()
            val cx0 = crop.x + crop.width / 2f
            val cy0 = crop.y + crop.height / 2f
            val invR2 = 1f / ((crop.width / 2f) * (crop.width / 2f) + (crop.height / 2f) * (crop.height / 2f))
            for (j in 0 until sh) {
                val ay = rect.y + j
                val py = ay - crop.y
                for (i in 0 until sw) {
                    val ax = rect.x + i
                    val px = ax - crop.x
                    val o = (j * sw + i) * 3
                    var gain = 1f
                    if (vig != 0f) {
                        val dx = ax - cx0
                        val dy = ay - cy0
                        val rr = (dx * dx + dy * dy) * invR2 // 0 centre .. 1 corner
                        gain *= exp2(-vig * rr * rr)
                    }
                    if (low != null) {
                        val r = scene[o]; val g = scene[o + 1]; val b = scene[o + 2]
                        val y = max(0.2627f * r + 0.6780f * g + 0.0593f * b, 1e-5f)
                        val l = log2(y)
                        val llProxy = sampleProxyMap(low, px, py)
                        // Where the full-resolution pixel disagrees strongly with the upsampled
                        // low-pass it sits on an edge the proxy cannot resolve: trust the pixel.
                        val dev = kotlin.math.abs(l - llProxy)
                        val tEdge = ((dev - 0.5f) / 1.0f).coerceIn(0f, 1f)
                        val wEdge = tEdge * tEdge * (3f - 2f * tEdge)
                        val ll = llProxy + (l - llProxy) * wEdge
                        val midMask = 1f - min(1f, kotlin.math.abs(l - LOG2_MID) / 5f)
                        var e = params.clarity * (l - ll) * midMask - params.localTone * (ll - LOG2_MID)
                        if (e > 1.2f) e = 1.2f else if (e < -1.2f) e = -1.2f
                        gain *= exp2(e)
                    }
                    if (gain != 1f) {
                        scene[o] *= gain; scene[o + 1] *= gain; scene[o + 2] *= gain
                    }
                    if (hal != null) {
                        val hv = sampleProxyMap(hal, px, py) * halAmt
                        scene[o] += hv * halTint[0]; scene[o + 1] += hv * halTint[1]; scene[o + 2] += hv * halTint[2]
                    }
                }
            }
        }

        /**
         * Edge-preserving NR in linear light. Luma: 5x5 range-weighted filter whose range scale is
         * the local noise sigma from the sensor noise model — so real texture (well above noise) is
         * kept. Chroma: sparse 5x5 taps at stride 2 (9x9 support), guided by luma similarity.
         * A residual of the original noise is always retained to avoid waxy surfaces.
         */
        private fun noiseReduce(sw: Int, sh: Int) {
            val sL = params.nrLuma
            val sC = params.nrChroma
            System.arraycopy(scene, 0, nr, 0, sw * sh * 3)
            if (sL <= 0f && sC <= 0f) return
            val wr = 0.2627f; val wg = 0.6780f; val wb = 0.0593f
            for (j in 4 until sh - 4) for (i in 4 until sw - 4) {
                val c = (j * sw + i) * 3
                val rc = scene[c]; val gc = scene[c + 1]; val bc = scene[c + 2]
                val yc = wr * rc + wg * gc + wb * bc
                val sigma = source.noiseSigma(yc)
                // ---- luma
                var yAcc = 0f
                var wAcc = 0f
                if (sL > 0f) {
                    val h2 = (2.2f * sigma) * (2.2f * sigma) + 1e-12f
                    for (dy in -2..2) for (dx in -2..2) {
                        val q = ((j + dy) * sw + i + dx) * 3
                        val yq = wr * scene[q] + wg * scene[q + 1] + wb * scene[q + 2]
                        val d = yq - yc
                        var wgt = 1f - d * d / h2
                        if (wgt <= 0f) continue
                        wgt *= SPATIAL5[(dy + 2) * 5 + dx + 2]
                        yAcc += yq * wgt
                        wAcc += wgt
                    }
                }
                val yNr = if (wAcc > 0f) yc + sL * (yAcc / wAcc - yc) else yc
                // ---- chroma (as differences from luma)
                var crc = rc - yc
                var cbc = bc - yc
                if (sC > 0f) {
                    val h2 = (3f * sigma) * (3f * sigma) + 1e-12f
                    var crA = 0f; var cbA = 0f; var wA = 0f
                    var dy = -4
                    while (dy <= 4) {
                        var dx = -4
                        while (dx <= 4) {
                            val q = ((j + dy) * sw + i + dx) * 3
                            val rq = scene[q]; val gq = scene[q + 1]; val bq = scene[q + 2]
                            val yq = wr * rq + wg * gq + wb * bq
                            val d = yq - yc
                            var wgt = 1f - d * d / h2
                            if (wgt > 0f) {
                                wgt *= SPATIAL9[((dy + 4) / 2) * 5 + (dx + 4) / 2]
                                crA += (rq - yq) * wgt; cbA += (bq - yq) * wgt; wA += wgt
                            }
                            dx += 2
                        }
                        dy += 2
                    }
                    if (wA > 0f) {
                        crc += sC * (crA / wA - crc)
                        cbc += sC * (cbA / wA - cbc)
                    }
                }
                val r = crc + yNr
                val b = cbc + yNr
                val g = (yNr - wr * r - wb * b) / wg
                nr[c] = r; nr[c + 1] = g; nr[c + 2] = b
            }
        }

        private fun lutPass(sw: Int, dw: Int, dh: Int, off: Int) {
            val lut = params.lut
            for (j in 0 until dh) for (i in 0 until dw) {
                val s = ((j + off) * sw + i + off) * 3
                val d = (j * dw + i) * 3
                lut.lookup(
                    LutBaker.SceneLog.encode(nr[s]),
                    LutBaker.SceneLog.encode(nr[s + 1]),
                    LutBaker.SceneLog.encode(nr[s + 2]),
                    lutOut,
                )
                disp[d] = lutOut[0]; disp[d + 1] = lutOut[1]; disp[d + 2] = lutOut[2]
            }
        }

        private fun sharpenGrainPack(r0: Int, w: Int, rows: Int, dw: Int, sink: StripeSink): Long {
            val amt = params.sharpenAmount
            val halo = params.haloLimit
            val grain = params.grain
            val grainAmt = (grain?.amount ?: 0.0).toFloat()
            // Grain cell size in pixels: spec size 1.0 ≈ 1/1000 of the short side.
            val cell = ((grain?.size ?: 1.0) * shortSide / 1000.0).toFloat().coerceAtLeast(0.6f)
            val rough = (grain?.roughness ?: 0.5).toFloat()
            var clipped = 0L
            for (j in 0 until rows) {
                for (i in 0 until w) {
                    val c = ((j + 1) * dw + i + 1) * 3
                    var r = disp[c]; var g = disp[c + 1]; var b = disp[c + 2]
                    if (amt > 0f) {
                        var blur = 0f
                        var mn = 2f
                        var mx = -1f
                        for (dy in -1..1) for (dx in -1..1) {
                            val q = ((j + 1 + dy) * dw + i + 1 + dx) * 3
                            val yq = 0.2126f * disp[q] + 0.7152f * disp[q + 1] + 0.0722f * disp[q + 2]
                            blur += yq * GAUSS3[(dy + 1) * 3 + dx + 1]
                            if (yq < mn) mn = yq
                            if (yq > mx) mx = yq
                        }
                        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                        val detail = y - blur
                        // Soft threshold: ignore detail at the level of display-space noise.
                        val t = 0.004f
                        val ad = kotlin.math.abs(detail)
                        val kept = if (ad <= t) 0f else (ad - t) * kotlin.math.sign(detail)
                        var ys = y + amt * kept
                        if (ys > mx + halo) ys = mx + halo
                        if (ys < mn - halo) ys = mn - halo
                        val dlt = ys - y
                        r += dlt; g += dlt; b += dlt
                    }
                    if (grainAmt > 0f) {
                        val ax = (i).toFloat()
                        val ay = (r0 + j).toFloat()
                        val n = valueNoise(ax / cell, ay / cell, params.seed)
                        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                        val mask = 4f * y * (1f - y) // strongest in mid-tones like film
                        val gv = n * grainAmt * (0.35f + 0.65f * mask)
                        if (rough > 0f) {
                            val nc = valueNoise(ax / cell + 37.1f, ay / cell - 11.7f, params.seed + 7) * 0.25f * rough * grainAmt
                            r += gv + nc; g += gv; b += gv - nc
                        } else {
                            r += gv; g += gv; b += gv
                        }
                    }
                    if (r >= 0.999f && g >= 0.999f && b >= 0.999f) clipped++
                    val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                    outArgb[j * w + i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }
            sink.write(r0, w, rows, outArgb)
            return clipped
        }
    }

    companion object {
        private const val LOG2_MID = -2.473931f // log2(0.18)
        private val SPATIAL5 = FloatArray(25) { k ->
            val dx = k % 5 - 2; val dy = k / 5 - 2
            exp(-(dx * dx + dy * dy) / (2 * 1.3 * 1.3)).toFloat()
        }
        private val SPATIAL9 = FloatArray(25) { k ->
            val dx = (k % 5 - 2) * 2; val dy = (k / 5 - 2) * 2
            exp(-(dx * dx + dy * dy) / (2 * 3.0 * 3.0)).toFloat()
        }
        private val GAUSS3 = floatArrayOf(1f, 2f, 1f, 2f, 4f, 2f, 1f, 2f, 1f).map { it / 16f }.toFloatArray()

        fun log2(v: Float): Float = (ln(v.toDouble()) * 1.4426950408889634).toFloat()
        fun exp2(v: Float): Float = Math.pow(2.0, v.toDouble()).toFloat()

        private fun hash(x: Int, y: Int, seed: Int): Float {
            var h = x * 374761393 + y * 668265263 + seed * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFFFF) / 8388608f - 1f // [-1, 1)
        }

        /** Smooth value noise in [-1,1], deterministic and stateless (parallel-safe). */
        fun valueNoise(x: Float, y: Float, seed: Int): Float {
            val xi = kotlin.math.floor(x).toInt()
            val yi = kotlin.math.floor(y).toInt()
            val fx = x - xi
            val fy = y - yi
            val sx = fx * fx * (3 - 2 * fx)
            val sy = fy * fy * (3 - 2 * fy)
            val a = hash(xi, yi, seed)
            val b = hash(xi + 1, yi, seed)
            val c = hash(xi, yi + 1, seed)
            val d = hash(xi + 1, yi + 1, seed)
            val top = a + (b - a) * sx
            val bot = c + (d - c) * sx
            // Normalise variance loss of bilinear interpolation (~0.6 for smoothstep blend).
            return (top + (bot - top) * sy) * 1.55f
        }

        /** Self-guided filter (edge-preserving smoothing) with Gaussian windows; returns a new array. */
        fun guidedFilter(src: FloatArray, w: Int, h: Int, sigma: Float, eps: Float): FloatArray {
            val meanI = src.copyOf(); gaussianBlur(meanI, w, h, sigma)
            val meanII = FloatArray(src.size) { src[it] * src[it] }; gaussianBlur(meanII, w, h, sigma)
            val a = FloatArray(src.size) { val v = meanII[it] - meanI[it] * meanI[it]; v / (v + eps) }
            val b = FloatArray(src.size) { meanI[it] - a[it] * meanI[it] }
            gaussianBlur(a, w, h, sigma); gaussianBlur(b, w, h, sigma)
            return FloatArray(src.size) { a[it] * src[it] + b[it] }
        }

        /** Separable Gaussian blur in place (small proxy images only). */
        fun gaussianBlur(data: FloatArray, w: Int, h: Int, sigma: Float) {
            val r = (sigma * 2.5f).toInt().coerceAtLeast(1)
            val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2f * sigma * sigma)) }
            val ks = k.sum()
            for (i in k.indices) k[i] /= ks
            val tmp = FloatArray(data.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                for (t in -r..r) s += data[y * w + (x + t).coerceIn(0, w - 1)] * k[t + r]
                tmp[y * w + x] = s
            }
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                for (t in -r..r) s += tmp[(y + t).coerceIn(0, h - 1) * w + x] * k[t + r]
                data[y * w + x] = s
            }
        }

        fun sqrtF(v: Float) = sqrt(v)
    }
}

/** Writes stripes into a rotated full-frame ARGB buffer (tests / small images). */
class RotatingArgbBuffer(val srcWidth: Int, val srcHeight: Int, val rotation: Int) : StripeSink {
    val width = if (rotation % 180 == 0) srcWidth else srcHeight
    val height = if (rotation % 180 == 0) srcHeight else srcWidth
    val pixels = IntArray(width * height)

    override fun write(cropRowStart: Int, width: Int, rows: Int, argb: IntArray) {
        for (j in 0 until rows) {
            val sy = cropRowStart + j
            for (sx in 0 until width) {
                val (dx, dy) = Orientation.map(sx, sy, srcWidth, srcHeight, rotation)
                pixels[dy * this.width + dx] = argb[j * width + sx]
            }
        }
    }
}

object Orientation {
    /** Maps a source pixel to its position after clockwise rotation by 0/90/180/270 degrees. */
    fun map(x: Int, y: Int, w: Int, h: Int, rotation: Int): Pair<Int, Int> = when ((rotation % 360 + 360) % 360) {
        90 -> Pair(h - 1 - y, x)
        180 -> Pair(w - 1 - x, h - 1 - y)
        270 -> Pair(y, w - 1 - x)
        else -> Pair(x, y)
    }
}
