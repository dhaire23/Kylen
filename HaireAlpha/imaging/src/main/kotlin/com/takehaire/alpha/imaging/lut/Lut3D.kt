package com.takehaire.alpha.imaging.lut

import kotlin.math.floor

/**
 * Cubic 3D LUT, RGB float, red index fastest (identical ordering to the .cube format and to
 * OpenGL ES 3D textures with width = red). Tetrahedral interpolation.
 */
class Lut3D(val size: Int, val data: FloatArray, val title: String = "") {
    init {
        require(size in 2..129) { "LUT size $size out of range" }
        require(data.size == size * size * size * 3) { "LUT data has ${data.size} values, expected ${size * size * size * 3}" }
    }

    private val n1 = size - 1
    private val sG = size * 3
    private val sB = size * size * 3

    /** Inputs in [0,1] (clamped). Thread-safe. */
    fun lookup(r: Float, g: Float, b: Float, out: FloatArray, offset: Int = 0) {
        val fr = r.coerceIn(0f, 1f) * n1
        val fg = g.coerceIn(0f, 1f) * n1
        val fb = b.coerceIn(0f, 1f) * n1
        var ir = floor(fr).toInt(); if (ir >= n1) ir = n1 - 1
        var ig = floor(fg).toInt(); if (ig >= n1) ig = n1 - 1
        var ib = floor(fb).toInt(); if (ib >= n1) ib = n1 - 1
        val dr = fr - ir
        val dg = fg - ig
        val db = fb - ib
        val base = ib * sB + ig * sG + ir * 3
        val c000 = base
        val c111 = base + sB + sG + 3
        val cA: Int
        val cB: Int
        val w0: Float
        val w1: Float
        val w2: Float
        val w3: Float
        if (dr >= dg) {
            if (dg >= db) { // r > g > b
                cA = base + 3; cB = base + 3 + sG
                w0 = 1 - dr; w1 = dr - dg; w2 = dg - db; w3 = db
            } else if (dr >= db) { // r > b > g
                cA = base + 3; cB = base + 3 + sB
                w0 = 1 - dr; w1 = dr - db; w2 = db - dg; w3 = dg
            } else { // b > r > g
                cA = base + sB; cB = base + sB + 3
                w0 = 1 - db; w1 = db - dr; w2 = dr - dg; w3 = dg
            }
        } else {
            if (db >= dg) { // b > g > r
                cA = base + sB; cB = base + sB + sG
                w0 = 1 - db; w1 = db - dg; w2 = dg - dr; w3 = dr
            } else if (db >= dr) { // g > b > r
                cA = base + sG; cB = base + sG + sB
                w0 = 1 - dg; w1 = dg - db; w2 = db - dr; w3 = dr
            } else { // g > r > b
                cA = base + sG; cB = base + sG + 3
                w0 = 1 - dg; w1 = dg - dr; w2 = dr - db; w3 = db
            }
        }
        for (k in 0..2) {
            out[offset + k] = w0 * data[c000 + k] + w1 * data[cA + k] + w2 * data[cB + k] + w3 * data[c111 + k]
        }
    }

    fun toCube(): String = buildString {
        if (title.isNotEmpty()) append("TITLE \"").append(title).append("\"\n")
        append("LUT_3D_SIZE ").append(size).append('\n')
        append("DOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
        var i = 0
        while (i < data.size) {
            append("%.6f %.6f %.6f\n".format(java.util.Locale.ROOT, data[i], data[i + 1], data[i + 2]))
            i += 3
        }
    }

    /** RGBA half-float-friendly layout for GL upload (alpha = 1). */
    fun toRgba(): FloatArray {
        val out = FloatArray(size * size * size * 4)
        var j = 0
        var i = 0
        while (i < data.size) {
            out[j] = data[i]; out[j + 1] = data[i + 1]; out[j + 2] = data[i + 2]; out[j + 3] = 1f
            i += 3; j += 4
        }
        return out
    }

    companion object {
        fun identity(size: Int): Lut3D {
            val d = FloatArray(size * size * size * 3)
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                d[i++] = r / (size - 1f); d[i++] = g / (size - 1f); d[i++] = b / (size - 1f)
            }
            return Lut3D(size, d, "identity")
        }
    }
}

class CubeParseException(message: String) : Exception(message)

/**
 * Strict Adobe/Resolve .cube 3D LUT parser. Rejects 1D LUTs, malformed lines, wrong value counts,
 * non-finite values and unreasonable domains rather than silently producing a broken look.
 * Imported LUTs are assumed to map sRGB-encoded input to sRGB-encoded output (documented to users).
 */
object CubeParser {
    fun parse(text: String): Lut3D {
        var size = -1
        var title = ""
        var dMin = floatArrayOf(0f, 0f, 0f)
        var dMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        text.lineSequence().forEachIndexed { lineNo, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("\\s+"))
            when (parts[0].uppercase()) {
                "TITLE" -> title = line.substringAfter("TITLE").trim().trim('"')
                "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull()
                    ?: throw CubeParseException("Bad LUT_3D_SIZE on line ${lineNo + 1}")
                "LUT_1D_SIZE" -> throw CubeParseException("1D LUTs are not supported")
                "DOMAIN_MIN" -> dMin = parseTriple(parts, lineNo)
                "DOMAIN_MAX" -> dMax = parseTriple(parts, lineNo)
                "LUT_3D_INPUT_RANGE" -> {
                    val lo = parts.getOrNull(1)?.toFloatOrNull() ?: throw CubeParseException("Bad input range")
                    val hi = parts.getOrNull(2)?.toFloatOrNull() ?: throw CubeParseException("Bad input range")
                    dMin = floatArrayOf(lo, lo, lo); dMax = floatArrayOf(hi, hi, hi)
                }
                else -> {
                    if (parts[0][0].isLetter()) return@forEachIndexed // unknown keyword: ignore per spec
                    val t = parseTriple(listOf("") + parts, lineNo)
                    values.add(t[0]); values.add(t[1]); values.add(t[2])
                }
            }
        }
        if (size < 2 || size > 129) throw CubeParseException("Missing or unsupported LUT_3D_SIZE ($size)")
        val expected = size * size * size * 3
        if (values.size != expected) throw CubeParseException("Expected ${expected / 3} entries, found ${values.size / 3}")
        for (i in 0..2) {
            if (dMin[i] != 0f || dMax[i] != 1f) throw CubeParseException("Only DOMAIN 0..1 is supported")
        }
        val arr = FloatArray(expected)
        for (i in 0 until expected) {
            val v = values[i]
            if (!v.isFinite()) throw CubeParseException("Non-finite LUT value")
            if (v < -0.5f || v > 1.5f) throw CubeParseException("LUT value $v outside plausible range")
            arr[i] = v.coerceIn(0f, 1f)
        }
        return Lut3D(size, arr, title)
    }

    private fun parseTriple(parts: List<String>, lineNo: Int): FloatArray {
        if (parts.size < 4) throw CubeParseException("Expected 3 values on line ${lineNo + 1}")
        return FloatArray(3) { parts[it + 1].toFloatOrNull() ?: throw CubeParseException("Bad number on line ${lineNo + 1}") }
    }
}
