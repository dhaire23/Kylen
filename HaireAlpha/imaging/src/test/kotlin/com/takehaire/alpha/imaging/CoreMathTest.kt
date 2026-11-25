package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.Mat3
import com.takehaire.alpha.imaging.color.Vec3
import com.takehaire.alpha.imaging.lut.CubeParseException
import com.takehaire.alpha.imaging.lut.CubeParser
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.imaging.tone.ToneCurve
import com.takehaire.alpha.imaging.tone.ToneParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

class CoreMathTest {

    @Test fun matrixInverseRoundTrips() {
        val m = Mat3.of(0.68, 0.18, 0.10, 0.28, 0.82, -0.10, 0.02, -0.15, 1.10)
        assertTrue((m * m.inverse()).approxEquals(Mat3.IDENTITY, 1e-10))
    }

    @Test fun srgbTransferRoundTrips() {
        for (i in 0..1000) {
            val v = i / 1000.0
            assertEquals(v, ColorMath.srgbDecode(ColorMath.srgbEncode(v)), 1e-9)
        }
        assertEquals(0.4613, ColorMath.srgbEncode(0.18), 1e-3)
    }

    @Test fun srgbWhiteIsD65AndLabWhiteIs100() {
        val white = ColorMath.SRGB_TO_XYZ * Vec3.ONE
        assertEquals(0.95047, white.x, 1e-4); assertEquals(1.0, white.y, 1e-6); assertEquals(1.08883, white.z, 1e-4)
        val lab = ColorMath.xyzToLab(white)
        assertEquals(100.0, lab[0], 1e-4); assertEquals(0.0, lab[1], 1e-3); assertEquals(0.0, lab[2], 1e-3)
    }

    @Test fun rec2020AndSrgbShareWhite() {
        val w = ColorMath.REC2020_TO_SRGB * Vec3.ONE
        assertEquals(1.0, w.x, 5e-4); assertEquals(1.0, w.y, 5e-4); assertEquals(1.0, w.z, 5e-4)
    }

    @Test fun bradfordMapsWhiteToWhite() {
        val d = ColorMath.D50_TO_D65 * ColorMath.D50
        assertEquals(ColorMath.D65.x, d.x, 1e-9); assertEquals(ColorMath.D65.z, d.z, 1e-9)
    }

    /** Sharma, Wu, Dalal (2005) CIEDE2000 test data, selected pairs. */
    @Test fun deltaE2000MatchesReferenceData() {
        val cases = listOf(
            Triple(doubleArrayOf(50.0, 2.6772, -79.7751), doubleArrayOf(50.0, 0.0, -82.7485), 2.0425),
            Triple(doubleArrayOf(50.0, 3.1571, -77.2803), doubleArrayOf(50.0, 0.0, -82.7485), 2.8615),
            Triple(doubleArrayOf(50.0, 2.8361, -74.0200), doubleArrayOf(50.0, 0.0, -82.7485), 3.4412),
            Triple(doubleArrayOf(50.0, -1.0, 2.0), doubleArrayOf(50.0, 0.0, 0.0), 2.3669),
            Triple(doubleArrayOf(50.0, 2.5, 0.0), doubleArrayOf(73.0, 25.0, -18.0), 27.1492),
            Triple(doubleArrayOf(50.0, 2.5, 0.0), doubleArrayOf(61.0, -5.0, 29.0), 22.8977),
            Triple(doubleArrayOf(60.2574, -34.0099, 36.2677), doubleArrayOf(60.4626, -34.1751, 39.4387), 1.2644),
            Triple(doubleArrayOf(22.7233, 20.0904, -46.6940), doubleArrayOf(23.0331, 14.9730, -42.5619), 2.0373),
        )
        for ((a, b, e) in cases) assertEquals(e, ColorMath.deltaE2000(a, b), 1e-3)
    }

    @Test fun cctOfD65IsAbout6504KSlightlyGreen() {
        val r = ColorMath.xyToCct(ColorMath.D65_XY[0], ColorMath.D65_XY[1])
        assertEquals(6504.0, r[0], 40.0)
        assertEquals(0.0032, r[1], 0.0008)
    }

    @Test fun cctRoundTripsWithDuv() {
        for (k in listOf(2500.0, 3200.0, 4300.0, 5500.0, 7500.0, 10000.0)) for (duv in listOf(-0.005, 0.0, 0.006)) {
            val xy = ColorMath.cctToXy(k, duv)
            val back = ColorMath.xyToCct(xy[0], xy[1])
            assertEquals(k, back[0], k * 0.01)
            assertEquals(duv, back[1], 0.0006)
        }
    }

    @Test fun oklabRoundTripsAndIsAchromaticForGrey() {
        val o = DoubleArray(3)
        val back = DoubleArray(3)
        ColorMath.linearSrgbToOklab(0.18, 0.18, 0.18, o)
        assertEquals(0.0, o[1], 1e-4); assertEquals(0.0, o[2], 1e-4)
        ColorMath.linearSrgbToOklab(0.7, 0.2, 0.05, o)
        ColorMath.oklabToLinearSrgb(o[0], o[1], o[2], back)
        assertEquals(0.7, back[0], 1e-6); assertEquals(0.2, back[1], 1e-6); assertEquals(0.05, back[2], 1e-6)
    }

    // ------------------------------------------------------------------ tone curve
    @Test fun toneCurveIsMonotoneAndHitsAnchors() {
        for (w in listOf(2.47, 3.0, 4.0, 6.0)) for (p in listOf(ToneParams(), ToneParams(contrast = 1.3, shadowDepth = 0.3), ToneParams(contrast = 0.8, shoulder = 0.9))) {
            val c = ToneCurve(p, w)
            var prev = -1.0
            var ev = -12.0
            while (ev <= w + 1) {
                val y = c.encoded(ev)
                assertTrue("monotone at $ev w=$w $p", y >= prev - 1e-12)
                prev = y
                ev += 0.01
            }
            assertEquals(p.midOut, c.encoded(0.0), 1e-9)
            assertEquals(1.0, c.encoded(w), 1e-9)
            assertEquals(0.0, c.encoded(-12.0), 1e-9)
        }
    }

    @Test fun toneCurveSlopeContinuousAtMidGrey() {
        for (p in listOf(ToneParams(), ToneParams(shadowDepth = 0.3), ToneParams(contrast = 1.2))) {
            val c = ToneCurve(p, 3.0)
            val h = 1e-4
            val left = (c.encoded(0.0) - c.encoded(-h)) / h
            val right = (c.encoded(h) - c.encoded(0.0)) / h
            assertEquals("slope continuity $p", left, right, 0.01)
        }
    }

    @Test fun toneCurveDeepensShadowsVersusPlainSrgb() {
        val c = ToneCurve(ToneParams(), 3.0)
        // Three stops under middle grey: plain sRGB encode gives 0.163; photographic toe is darker.
        assertTrue(c.encoded(-3.0) < ColorMath.srgbEncode(0.18 / 8))
        assertTrue(c.encoded(-3.0) > 0.08)
    }

    @Test fun toneCurveInverseRoundTrips() {
        val c = ToneCurve(ToneParams(), 3.0)
        for (ev in listOf(-6.0, -2.0, 0.0, 1.0, 2.5)) assertEquals(ev, c.inverseEncoded(c.encoded(ev)), 1e-4)
    }

    // ------------------------------------------------------------------ LUT
    @Test fun identityLutIsExactAndTetrahedralReproducesLinearFunctions() {
        val lut = Lut3D.identity(17)
        val o = FloatArray(3)
        val rnd = java.util.Random(3)
        repeat(1000) {
            val r = rnd.nextFloat(); val g = rnd.nextFloat(); val b = rnd.nextFloat()
            lut.lookup(r, g, b, o)
            assertEquals(r, o[0], 1e-5f); assertEquals(g, o[1], 1e-5f); assertEquals(b, o[2], 1e-5f)
        }
        // An affine colour matrix must be reproduced exactly by tetrahedral interpolation.
        val n = 9
        val d = FloatArray(n * n * n * 3)
        var i = 0
        for (bb in 0 until n) for (gg in 0 until n) for (rr in 0 until n) {
            val r = rr / 8f; val g = gg / 8f; val b = bb / 8f
            d[i++] = 0.7f * r + 0.2f * g + 0.1f * b; d[i++] = 0.1f * r + 0.8f * g + 0.1f * b; d[i++] = 0.05f * r + 0.15f * g + 0.8f * b
        }
        val m = Lut3D(n, d)
        repeat(500) {
            val r = rnd.nextFloat(); val g = rnd.nextFloat(); val b = rnd.nextFloat()
            m.lookup(r, g, b, o)
            assertEquals(0.7f * r + 0.2f * g + 0.1f * b, o[0], 1e-5f)
            assertEquals(0.05f * r + 0.15f * g + 0.8f * b, o[2], 1e-5f)
        }
    }

    @Test fun cubeRoundTripAndValidation() {
        val lut = Lut3D.identity(5)
        val parsed = CubeParser.parse(lut.toCube())
        assertEquals(5, parsed.size)
        for (k in lut.data.indices) assertEquals(lut.data[k], parsed.data[k], 1e-5f)
        val bad = listOf(
            "LUT_3D_SIZE 2\n0 0 0\n1 0 0\n",
            "LUT_1D_SIZE 4\n0 0 0\n",
            "LUT_3D_SIZE 2\n" + "0 0 nan\n".repeat(8),
            "LUT_3D_SIZE 2\nDOMAIN_MAX 2 2 2\n" + "0 0 0\n".repeat(8),
            "0 0 0\n",
        )
        for (b in bad) {
            try { CubeParser.parse(b); fail("should reject: $b") } catch (_: CubeParseException) {}
        }
    }

    @Test fun srgbContrastCurveMatchesSrgbWithinHalfACode() {
        val pts = com.takehaire.alpha.imaging.color.SrgbCurvePoints.points(64)
        assertEquals(0f, pts[0], 0f); assertEquals(1f, pts[pts.size - 2], 0f); assertEquals(1f, pts.last(), 1e-6f)
        var maxErr = 0.0
        for (i in 0..4000) {
            val x = i / 4000.0
            val e = abs(com.takehaire.alpha.imaging.color.SrgbCurvePoints.eval(pts, x.toFloat()) - ColorMath.srgbEncode(x))
            maxErr = maxOf(maxErr, e)
        }
        assertTrue("max error $maxErr", maxErr < 0.5 / 255)
    }

    @Test fun skinHueCentreMatchesMonkSkinToneSwatches() {
        // Validates the skin region constant against real-world skin appearance data.
        val o = DoubleArray(3)
        val hues = (0 until 10).map {
            val c = MonkSkinTones.linearSrgb(it)
            ColorMath.linearSrgbToOklab(c[0], c[1], c[2], o)
            ColorMath.hueDeg(o[1], o[2])
        }
        val mean = hues.average()
        assertTrue("skin hues $hues", hues.all { abs(it - com.takehaire.alpha.imaging.look.LookRenderer.SKIN_HUE_CENTER) < 32 })
        assertTrue("mean skin hue $mean", abs(mean - com.takehaire.alpha.imaging.look.LookRenderer.SKIN_HUE_CENTER) < 15)
    }
}
