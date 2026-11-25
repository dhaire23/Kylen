package com.takehaire.alpha.imaging.color

import kotlin.math.abs

/**
 * Immutable row-major 3x3 matrix in double precision. Used for colour-space and
 * calibration maths (never in per-pixel inner loops; see [toFloatArray]).
 */
class Mat3(private val m: DoubleArray) {
    init {
        require(m.size == 9) { "Mat3 needs 9 elements" }
    }

    operator fun get(r: Int, c: Int): Double = m[r * 3 + c]

    operator fun times(o: Mat3): Mat3 {
        val out = DoubleArray(9)
        for (r in 0 until 3) for (c in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += this[r, k] * o[k, c]
            out[r * 3 + c] = s
        }
        return Mat3(out)
    }

    operator fun times(v: Vec3): Vec3 = Vec3(
        this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z,
        this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z,
        this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z,
    )

    fun scale(s: Double): Mat3 = Mat3(DoubleArray(9) { m[it] * s })

    fun determinant(): Double =
        this[0, 0] * (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) -
            this[0, 1] * (this[1, 0] * this[2, 2] - this[1, 2] * this[2, 0]) +
            this[0, 2] * (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0])

    fun inverse(): Mat3 {
        val det = determinant()
        require(abs(det) > 1e-12) { "Singular matrix" }
        val inv = DoubleArray(9)
        inv[0] = (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) / det
        inv[1] = (this[0, 2] * this[2, 1] - this[0, 1] * this[2, 2]) / det
        inv[2] = (this[0, 1] * this[1, 2] - this[0, 2] * this[1, 1]) / det
        inv[3] = (this[1, 2] * this[2, 0] - this[1, 0] * this[2, 2]) / det
        inv[4] = (this[0, 0] * this[2, 2] - this[0, 2] * this[2, 0]) / det
        inv[5] = (this[0, 2] * this[1, 0] - this[0, 0] * this[1, 2]) / det
        inv[6] = (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0]) / det
        inv[7] = (this[0, 1] * this[2, 0] - this[0, 0] * this[2, 1]) / det
        inv[8] = (this[0, 0] * this[1, 1] - this[0, 1] * this[1, 0]) / det
        return Mat3(inv)
    }

    fun toFloatArray(): FloatArray = FloatArray(9) { m[it].toFloat() }
    fun toDoubleArray(): DoubleArray = m.copyOf()

    /** Each row sums to 1 → neutral-preserving. Returns a copy with rows normalised. */
    fun rowNormalized(): Mat3 {
        val out = m.copyOf()
        for (r in 0 until 3) {
            val s = out[r * 3] + out[r * 3 + 1] + out[r * 3 + 2]
            if (abs(s) > 1e-12) for (c in 0 until 3) out[r * 3 + c] /= s
        }
        return Mat3(out)
    }

    fun approxEquals(o: Mat3, eps: Double = 1e-6): Boolean = (0 until 9).all { abs(m[it] - o.m[it]) < eps }

    override fun toString(): String = (0 until 3).joinToString(prefix = "[", postfix = "]", separator = "; ") { r ->
        (0 until 3).joinToString(" ") { c -> "%.6f".format(this[r, c]) }
    }

    companion object {
        val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))
        fun of(vararg v: Double) = Mat3(v.copyOf())
        fun diag(a: Double, b: Double, c: Double) = Mat3(doubleArrayOf(a, 0.0, 0.0, 0.0, b, 0.0, 0.0, 0.0, c))
        fun diag(v: Vec3) = diag(v.x, v.y, v.z)
        fun fromFloats(f: FloatArray) = Mat3(DoubleArray(9) { f[it].toDouble() })
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    fun maxComponent() = maxOf(x, y, z)
    fun minComponent() = minOf(x, y, z)
    operator fun get(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }
    companion object {
        val ONE = Vec3(1.0, 1.0, 1.0)
    }
}
