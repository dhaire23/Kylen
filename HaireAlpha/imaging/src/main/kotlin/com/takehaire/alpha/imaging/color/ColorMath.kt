package com.takehaire.alpha.imaging.color

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standard colorimetry. All constants are published standard values
 * (IEC 61966-2-1 sRGB, ITU-R BT.2020, Bradford CAT, CIE 1976 L*a*b*, Oklab by B. Ottosson).
 */
object ColorMath {
    val D50 = Vec3(0.96422, 1.0, 0.82521)
    val D65 = Vec3(0.95047, 1.0, 1.08883)
    val D50_XY = doubleArrayOf(0.34567, 0.35850)
    val D65_XY = doubleArrayOf(0.31271, 0.32902)

    /** Linear sRGB (D65) → XYZ (D65). */
    val SRGB_TO_XYZ = Mat3.of(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041,
    )
    val XYZ_TO_SRGB: Mat3 = SRGB_TO_XYZ.inverse()

    /** Linear Rec.2020 (D65) → XYZ (D65). */
    val REC2020_TO_XYZ = Mat3.of(
        0.6369580, 0.1446169, 0.1688810,
        0.2627002, 0.6779981, 0.0593017,
        0.0000000, 0.0280727, 1.0609851,
    )
    val XYZ_TO_REC2020: Mat3 = REC2020_TO_XYZ.inverse()

    val REC2020_TO_SRGB: Mat3 = XYZ_TO_SRGB * REC2020_TO_XYZ
    val SRGB_TO_REC2020: Mat3 = XYZ_TO_REC2020 * SRGB_TO_XYZ

    private val BRADFORD = Mat3.of(
        0.8951, 0.2664, -0.1614,
        -0.7502, 1.7135, 0.0367,
        0.0389, -0.0685, 1.0296,
    )
    private val BRADFORD_INV = BRADFORD.inverse()

    /** Bradford chromatic adaptation from source white XYZ to destination white XYZ. */
    fun bradford(srcWhite: Vec3, dstWhite: Vec3): Mat3 {
        val s = BRADFORD * srcWhite
        val d = BRADFORD * dstWhite
        return BRADFORD_INV * Mat3.diag(d.x / s.x, d.y / s.y, d.z / s.z) * BRADFORD
    }

    val D50_TO_D65: Mat3 = bradford(D50, D65)
    val D65_TO_D50: Mat3 = bradford(D65, D50)

    // Luminance weights (Y row) of the working spaces.
    val REC2020_Y = doubleArrayOf(0.2627002, 0.6779981, 0.0593017)
    val SRGB_Y = doubleArrayOf(0.2126729, 0.7151522, 0.0721750)

    // ---------------------------------------------------------------- transfer functions
    fun srgbEncode(v: Double): Double = when {
        v <= 0.0 -> 0.0
        v <= 0.0031308 -> 12.92 * v
        v >= 1.0 -> 1.0
        else -> 1.055 * v.pow(1.0 / 2.4) - 0.055
    }

    fun srgbDecode(e: Double): Double = when {
        e <= 0.0 -> 0.0
        e <= 0.04045 -> e / 12.92
        e >= 1.0 -> 1.0
        else -> ((e + 0.055) / 1.055).pow(2.4)
    }

    fun srgbEncodeF(v: Float): Float = srgbEncode(v.toDouble()).toFloat()
    fun srgbDecodeF(e: Float): Float = srgbDecode(e.toDouble()).toFloat()

    // ---------------------------------------------------------------- xy / uv helpers
    fun xyToXYZ(x: Double, y: Double, Y: Double = 1.0): Vec3 = Vec3(x * Y / y, Y, (1.0 - x - y) * Y / y)

    fun xyzToXy(v: Vec3): DoubleArray {
        val s = v.x + v.y + v.z
        return if (s <= 0.0) D50_XY.copyOf() else doubleArrayOf(v.x / s, v.y / s)
    }

    /** CIE 1960 UCS (u, v) from xy. */
    fun xyToUv(x: Double, y: Double): DoubleArray {
        val d = -2.0 * x + 12.0 * y + 3.0
        return doubleArrayOf(4.0 * x / d, 6.0 * y / d)
    }

    fun uvToXy(u: Double, v: Double): DoubleArray {
        val d = 2.0 * u - 8.0 * v + 4.0
        return doubleArrayOf(3.0 * u / d, 2.0 * v / d)
    }

    // ---------------------------------------------------------------- CCT
    /**
     * Planckian locus chromaticity, Kim et al. (2002) cubic-spline approximation, valid 1667K–25000K.
     */
    fun planckianXy(kelvin: Double): DoubleArray {
        val t = kelvin.coerceIn(1667.0, 25000.0)
        val t2 = t * t
        val t3 = t2 * t
        val x = if (t <= 4000.0) {
            -0.2661239e9 / t3 - 0.2343589e6 / t2 + 0.8776956e3 / t + 0.179910
        } else {
            -3.0258469e9 / t3 + 2.1070379e6 / t2 + 0.2226347e3 / t + 0.240390
        }
        val x2 = x * x
        val x3 = x2 * x
        val y = when {
            t <= 2222.0 -> -1.1063814 * x3 - 1.34811020 * x2 + 2.18555832 * x - 0.20219683
            t <= 4000.0 -> -0.9549476 * x3 - 1.37418593 * x2 + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x3 - 5.87338670 * x2 + 3.75112997 * x - 0.37001483
        }
        return doubleArrayOf(x, y)
    }

    /**
     * White point for a correlated colour temperature and Duv offset (perpendicular to the
     * Planckian locus in CIE 1960 uv; positive Duv = above the locus = greener).
     */
    fun cctToXy(kelvin: Double, duv: Double = 0.0): DoubleArray {
        val p = planckianXy(kelvin)
        if (duv == 0.0) return p
        val uv = xyToUv(p[0], p[1])
        val p2 = planckianXy(kelvin * 1.001)
        val uv2 = xyToUv(p2[0], p2[1])
        var du = uv2[0] - uv[0]
        var dv = uv2[1] - uv[1]
        val len = hypot(du, dv)
        du /= len; dv /= len
        // Normal pointing "up" (towards green) is (-dv, du) when temperature increases leftwards.
        var nu = -dv
        var nv = du
        if (nv < 0) { nu = -nu; nv = -nv }
        return uvToXy(uv[0] + nu * duv, uv[1] + nv * duv)
    }

    /**
     * Inverse of [cctToXy]: numerical search of the nearest locus point in uv (golden-section in mired).
     * Returns (kelvin, duv).
     */
    fun xyToCct(x: Double, y: Double): DoubleArray {
        val target = xyToUv(x, y)
        fun dist(mired: Double): Double {
            val p = planckianXy(1e6 / mired)
            val uv = xyToUv(p[0], p[1])
            return hypot(uv[0] - target[0], uv[1] - target[1])
        }
        var a = 1e6 / 25000.0
        var b = 1e6 / 1667.0
        val gr = (sqrt(5.0) - 1) / 2
        var c = b - gr * (b - a)
        var d = a + gr * (b - a)
        repeat(80) {
            if (dist(c) < dist(d)) b = d else a = c
            c = b - gr * (b - a)
            d = a + gr * (b - a)
        }
        val mired = (a + b) / 2
        val k = 1e6 / mired
        val p = planckianXy(k)
        val uv = xyToUv(p[0], p[1])
        val dd = hypot(uv[0] - target[0], uv[1] - target[1])
        val sign = if (target[1] >= uv[1]) 1.0 else -1.0
        return doubleArrayOf(k, sign * dd)
    }

    // ---------------------------------------------------------------- CIE Lab (D50 or D65 reference)
    private fun labF(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0

    fun xyzToLab(v: Vec3, white: Vec3 = D65): DoubleArray {
        val fx = labF(v.x / white.x)
        val fy = labF(v.y / white.y)
        val fz = labF(v.z / white.z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    fun labToXyz(lab: DoubleArray, white: Vec3 = D65): Vec3 {
        val fy = (lab[0] + 16.0) / 116.0
        val fx = fy + lab[1] / 500.0
        val fz = fy - lab[2] / 200.0
        fun inv(f: Double): Double {
            val f3 = f * f * f
            return if (f3 > 216.0 / 24389.0) f3 else (116.0 * f - 16.0) / (24389.0 / 27.0)
        }
        return Vec3(inv(fx) * white.x, inv(fy) * white.y, inv(fz) * white.z)
    }

    fun linearSrgbToLab(r: Double, g: Double, b: Double): DoubleArray = xyzToLab(SRGB_TO_XYZ * Vec3(r, g, b))

    /** CIEDE2000 colour difference (Sharma, Wu, Dalal 2005 reference formulation). */
    fun deltaE2000(lab1: DoubleArray, lab2: DoubleArray): Double {
        val (l1, a1, b1) = Triple(lab1[0], lab1[1], lab1[2])
        val (l2, a2, b2) = Triple(lab2[0], lab2[1], lab2[2])
        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cBar = (c1 + c2) / 2.0
        val cBar7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 25.0.pow(7.0))))
        val a1p = (1.0 + g) * a1
        val a2p = (1.0 + g) * a2
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)
        fun hue(b: Double, ap: Double): Double {
            if (b == 0.0 && ap == 0.0) return 0.0
            val h = Math.toDegrees(atan2(b, ap))
            return if (h < 0) h + 360.0 else h
        }
        val h1p = hue(b1, a1p)
        val h2p = hue(b2, a2p)
        val dLp = l2 - l1
        val dCp = c2p - c1p
        var dhp = 0.0
        if (c1p * c2p != 0.0) {
            dhp = h2p - h1p
            if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
        }
        val dHp = 2.0 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2.0))
        val lBarP = (l1 + l2) / 2.0
        val cBarP = (c1p + c2p) / 2.0
        var hBarP = h1p + h2p
        if (c1p * c2p != 0.0) {
            hBarP = if (abs(h1p - h2p) <= 180) (h1p + h2p) / 2.0
            else if (h1p + h2p < 360) (h1p + h2p + 360) / 2.0
            else (h1p + h2p - 360) / 2.0
        }
        val t = 1 - 0.17 * cos(Math.toRadians(hBarP - 30)) + 0.24 * cos(Math.toRadians(2 * hBarP)) +
            0.32 * cos(Math.toRadians(3 * hBarP + 6)) - 0.20 * cos(Math.toRadians(4 * hBarP - 63))
        val dTheta = 30 * exp(-((hBarP - 275) / 25).pow(2.0))
        val cBarP7 = cBarP.pow(7.0)
        val rc = 2 * sqrt(cBarP7 / (cBarP7 + 25.0.pow(7.0)))
        val sl = 1 + (0.015 * (lBarP - 50).pow(2.0)) / sqrt(20 + (lBarP - 50).pow(2.0))
        val sc = 1 + 0.045 * cBarP
        val sh = 1 + 0.015 * cBarP * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt(
            (dLp / sl).pow(2.0) + (dCp / sc).pow(2.0) + (dHp / sh).pow(2.0) + rt * (dCp / sc) * (dHp / sh),
        )
    }

    // ---------------------------------------------------------------- Oklab (linear sRGB primaries)
    fun linearSrgbToOklab(r: Double, g: Double, b: Double, out: DoubleArray) {
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        out[0] = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        out[1] = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        out[2] = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    }

    fun oklabToLinearSrgb(L: Double, a: Double, b: Double, out: DoubleArray) {
        val l = L + 0.3963377774 * a + 0.2158037573 * b
        val m = L - 0.1055613458 * a - 0.0638541728 * b
        val s = L - 0.0894841775 * a - 1.2914855480 * b
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        out[0] = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        out[1] = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        out[2] = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
    }

    /** Oklch hue in degrees [0, 360). */
    fun hueDeg(a: Double, b: Double): Double {
        val h = atan2(b, a) * 180.0 / PI
        return if (h < 0) h + 360.0 else h
    }

    fun circularDistanceDeg(a: Double, b: Double): Double {
        var d = abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }

    fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        if (e1 == e0) return if (x < e0) 0.0 else 1.0
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
