package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.color.Vec3
import com.takehaire.alpha.imaging.exposure.AutoIsoSettings
import com.takehaire.alpha.imaging.exposure.ExposureLimits
import com.takehaire.alpha.imaging.exposure.ExposureProgram
import com.takehaire.alpha.imaging.exposure.ExposureWarning
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.exposure.Stops
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.lens.CropClass
import com.takehaire.alpha.imaging.lens.LensMath
import com.takehaire.alpha.imaging.lens.OpticalSource
import com.takehaire.alpha.imaging.look.LookRenderer
import com.takehaire.alpha.imaging.look.Recipe
import com.takehaire.alpha.imaging.look.withRecipe
import com.takehaire.alpha.imaging.lut.LutBaker
import com.takehaire.alpha.imaging.process.DevelopParams
import com.takehaire.alpha.imaging.process.Orientation
import com.takehaire.alpha.imaging.process.RotatingArgbBuffer
import com.takehaire.alpha.imaging.process.StillDeveloper
import com.takehaire.alpha.imaging.raw.FloatSceneSource
import com.takehaire.alpha.imaging.raw.IntRect
import com.takehaire.alpha.imaging.raw.RawSceneSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PipelineTest {

    // ------------------------------------------------------------------ assets
    @Test fun assetsLoadWithUniqueIdsAndRequiredLooks() {
        val looks = Assets.looks.looks
        assertEquals(looks.size, looks.map { it.id }.toSet().size)
        for (id in listOf("alpha_natural", "standard", "neutral", "monochrome", "portra400", "forever", "grit", "cinestill800t"))
            assertNotNull(looks.firstOrNull { it.id == id })
        assertEquals(14, Assets.lenses.lenses.size)
        assertEquals("default", Assets.sensorProfile("SM-S948U", "0").id)
        assertTrue(Assets.looks.looks.none { it.vignette != 0.0 && it.id == "alpha_natural" })
    }

    @Test fun factoryDefaultLookHasNoGrainHalationOrVignette() {
        val n = Assets.alphaNatural
        assertEquals(null, n.grain); assertEquals(null, n.halation); assertEquals(0.0, n.vignette, 0.0)
    }

    @Test fun calibrationTablesInterpolateInLogIso() {
        val p = Assets.sensorProfile("x", "0")
        assertTrue(p.at(p.nrLuma, 50) < p.at(p.nrLuma, 3200))
        assertTrue(p.at(p.sharpen, 6400) < p.at(p.sharpen, 100))
    }

    // ------------------------------------------------------------------ DNG colour model
    @Test fun dngModelRecoversWhitePointFromNeutral() {
        val cam = SyntheticCamera()
        for (k in listOf(2856.0, 4000.0, 5500.0, 6504.0)) {
            val xy = ColorMath.planckianXy(k)
            val n = cam.camFromXyz * ColorMath.xyToXYZ(xy[0], xy[1])
            val found = cam.colorModel.neutralToXy(n / n.maxComponent())
            assertEquals(xy[0], found[0], 2e-3); assertEquals(xy[1], found[1], 2e-3)
        }
    }

    @Test fun neutralMapsToD50WhiteThroughForwardMatrix() {
        val cam = SyntheticCamera()
        val n = (cam.camFromXyz * ColorMath.D65).let { it / it.maxComponent() }
        val xyz = cam.colorModel.cameraToXyzD50(n) * n
        val xy = ColorMath.xyzToXy(xyz)
        assertEquals(ColorMath.D50_XY[0], xy[0], 1e-3); assertEquals(ColorMath.D50_XY[1], xy[1], 1e-3)
    }

    @Test fun whiteBalanceKelvinNeutralIsConsistentWithModel() {
        val cam = SyntheticCamera()
        val n = cam.colorModel.neutralForWhiteBalance(3200.0, 0.0)
        val xy = cam.colorModel.neutralToXy(n)
        val cct = ColorMath.xyToCct(xy[0], xy[1])
        assertEquals(3200.0, cct[0], 60.0)
    }

    // ------------------------------------------------------------------ RAW end to end
    @Test fun rawPipelineRecoversColorCheckerWithin2DeltaE() {
        for (fm in listOf(true, false)) {
            val result = Validation.colorCheckerThroughRaw(SyntheticCamera(useForwardMatrices = fm), ColorMath.D65_XY, iso = 100)
            assertTrue("mean ΔE00 ${result.mean} (FM=$fm)", result.mean < 1.0)
            assertTrue("max ΔE00 ${result.max} (FM=$fm)", result.max < 2.0)
        }
    }

    @Test fun rawPipelineUnderTungstenStaysAccurate() {
        val r = Validation.colorCheckerThroughRaw(SyntheticCamera(), doubleArrayOf(0.44757, 0.40745), iso = 400)
        assertTrue("mean ΔE00 ${r.mean}", r.mean < 1.5)
    }

    @Test fun twoDifferentSensorsAgreeAfterCalibration() {
        val a = Validation.colorCheckerThroughRaw(SyntheticCamera(), ColorMath.D65_XY, 100)
        val b = Validation.colorCheckerThroughRaw(SyntheticCamera(camFromXyz = SyntheticCamera.ALT_CAM_FROM_XYZ), ColorMath.D65_XY, 100)
        val d = a.labs.indices.map { ColorMath.deltaE2000(a.labs[it], b.labs[it]) }
        assertTrue("sensor-to-sensor mean ΔE ${d.average()}", d.average() < 1.0)
    }

    // ------------------------------------------------------------------ looks / renderer
    @Test fun middleGreyRendersNeutralAndAtHouseLevel() {
        val r = LookRenderer(Assets.alphaNatural, 3.0)
        val o = DoubleArray(3)
        r.render(0.18, 0.18, 0.18, o)
        assertEquals(0.46, o[1], 0.01)
        assertEquals(o[0], o[1], 0.004); assertEquals(o[2], o[1], 0.004)
    }

    @Test fun clippedSensorDataRendersWhite() {
        val r = LookRenderer(Assets.alphaNatural, 3.0)
        val o = DoubleArray(3)
        val clip = 0.18 * Math.pow(2.0, 3.0)
        r.render(clip, clip, clip, o)
        assertTrue(o.all { it > 0.995 })
    }

    @Test fun allLooksProduceValidOutputAndMonotoneGreyRamp() {
        val o = DoubleArray(3)
        for (look in Assets.looks.looks) {
            val r = LookRenderer(look, 3.0)
            var prev = -1.0
            for (i in 0..200) {
                val ev = -10 + i * 0.065
                val v = 0.18 * Math.pow(2.0, ev)
                r.render(v, v, v, o)
                assertTrue("${look.id} finite", o.all { it.isFinite() && it in 0.0..1.0 })
                val y = 0.2126 * o[0] + 0.7152 * o[1] + 0.0722 * o[2]
                assertTrue("${look.id} grey ramp monotone at ev=$ev ($y < $prev)", y >= prev - 2e-3)
                prev = y
            }
        }
    }

    @Test fun recipeAtZeroIsIdentityForColour() {
        val base = Assets.look("forever")
        val same = base.withRecipe(Recipe("r", "R", "forever"))
        val a = LookRenderer(base, 3.0)
        val b = LookRenderer(same, 3.0)
        val oa = DoubleArray(3); val ob = DoubleArray(3)
        for (c in listOf(doubleArrayOf(0.3, 0.12, 0.05), doubleArrayOf(0.05, 0.2, 0.4), doubleArrayOf(0.18, 0.18, 0.18))) {
            a.render(c[0], c[1], c[2], oa); b.render(c[0], c[1], c[2], ob)
            for (k in 0..2) assertEquals(oa[k], ob[k], 1e-9)
        }
    }

    @Test fun sceneLutMatchesDirectRenderer() {
        for (id in listOf("alpha_natural", "portra400", "forever", "classic_negative")) {
            val look = Assets.look(id)
            val lut = LutBaker.bakeScene(look, look, 1.0, 3.0)
            val r = LookRenderer(look, 3.0)
            val o = DoubleArray(3)
            val l = FloatArray(3)
            val rnd = java.util.Random(11)
            val realistic = ArrayList<Double>()
            var extremeMax = 0.0
            repeat(4000) {
                val base = -6 + rnd.nextDouble() * 8.5
                val spread = if (it % 2 == 0) 2.0 else 8.0 // realistic surfaces vs extreme Rec.2020 colours
                // Channels never exceed the sensor clip (whiteEv) because the RAW front end clips them there.
                val c = DoubleArray(3) { 0.18 * Math.pow(2.0, (base + (rnd.nextDouble() - 0.5) * spread).coerceIn(-10.5, 3.0)) }
                r.render(c[0], c[1], c[2], o)
                lut.lookup(LutBaker.SceneLog.encode(c[0].toFloat()), LutBaker.SceneLog.encode(c[1].toFloat()), LutBaker.SceneLog.encode(c[2].toFloat()), l)
                var e = 0.0
                for (k in 0..2) e = maxOf(e, abs(o[k] - l[k]))
                if (spread == 2.0) realistic.add(e) else extremeMax = maxOf(extremeMax, e)
            }
            realistic.sort()
            val p99 = realistic[(realistic.size * 0.99).toInt()]
            assertTrue("$id realistic mean ${realistic.average()}", realistic.average() < 0.002) // < 0.5 code
            assertTrue("$id realistic p99 $p99", p99 < 0.008) // < 2 codes
            assertTrue("$id extreme gamut max $extremeMax", extremeMax < 0.06)
        }
    }

    @Test fun intensityZeroEqualsBaseLook() {
        val lut0 = LutBaker.bakeScene(Assets.look("ektar100"), Assets.alphaNatural, 0.0, 3.0, size = 17)
        val base = LutBaker.bakeScene(Assets.alphaNatural, Assets.alphaNatural, 1.0, 3.0, size = 17)
        for (k in lut0.data.indices) assertEquals(base.data[k], lut0.data[k], 2e-4f)
    }

    // ------------------------------------------------------------------ developer
    @Test fun developerOutputsCorrectSizeAndOrientation() {
        val w = 64; val h = 40
        val rgb = FloatArray(w * h * 3) { 0.18f }
        // Mark the top-left pixel region bright.
        for (y in 0 until 4) for (x in 0 until 4) { val p = (y * w + x) * 3; rgb[p] = 1f; rgb[p + 1] = 1f; rgb[p + 2] = 1f }
        val lut = LutBaker.bakeScene(Assets.alphaNatural, Assets.alphaNatural, 1.0, 3.0, size = 33)
        for (rot in listOf(0, 90, 180, 270)) {
            val sink = RotatingArgbBuffer(w, h, rot)
            StillDeveloper(FloatSceneSource(rgb, w, h), DevelopParams(lut, IntRect(0, 0, w, h), threads = 2, stripeRows = 16)).develop(sink)
            val (cx, cy) = Orientation.map(1, 1, w, h, rot)
            val px = sink.pixels[cy * sink.width + cx]
            assertTrue("rot $rot bright corner", (px shr 8 and 0xFF) > 230)
            val mid = sink.pixels[(sink.height / 2) * sink.width + sink.width / 2]
            assertEquals("rot $rot mid grey", 117.0, (mid shr 8 and 0xFF).toDouble(), 4.0)
        }
    }

    @Test fun sharpeningDoesNotHaloStepEdges() {
        val r = Validation.edgeOvershoot(Assets.alphaNatural, ProcessingMode.NATURAL)
        assertTrue("overshoot ${r * 100}%", r < 0.05)
    }

    @Test fun lowIsoNrPreservesTextureHighIsoReducesNoise() {
        val tex = Validation.textureRetention(iso = 100)
        assertTrue("texture retention $tex", tex > 0.85)
        val nr = Validation.noiseReduction(iso = 3200)
        assertTrue("noise ratio $nr", nr < 0.85 && nr > 0.25) // reduced, but never smeared to plastic
    }

    @Test fun rawDevelopmentProducesNeutralGreyPatch() {
        val cam = SyntheticCamera()
        val w = 96; val h = 64
        val white = ColorMath.D65
        val scene = FloatArray(w * h * 3)
        for (i in 0 until w * h) { scene[i * 3] = (white.x * 0.18).toFloat(); scene[i * 3 + 1] = 0.18f; scene[i * 3 + 2] = (white.z * 0.18).toFloat() }
        val raw = cam.capture(scene, w, h, white, exposure = 1f)
        val src = RawSceneSource(raw, 1f)
        val out = FloatArray(32 * 32 * 3)
        src.fill(IntRect(32, 16, 32, 32), out)
        var r = 0.0; var g = 0.0; var b = 0.0
        for (i in 0 until 32 * 32) { r += out[i * 3]; g += out[i * 3 + 1]; b += out[i * 3 + 2] }
        val n = 32.0 * 32
        // DNG normalisation: the brightest camera channel of a neutral equals scene luminance.
        val expected = 0.18 * (cam.camFromXyz * white).maxComponent()
        assertEquals(expected, g / n, expected * 0.03)
        assertEquals(1.0, r / g, 0.02); assertEquals(1.0, b / g, 0.02)
    }

    // ------------------------------------------------------------------ exposure
    private val lim = ExposureLimits(1 / 20000.0, 0.5, 50, 3200)

    @Test fun apertureModeHoldsMinShutterAndRaisesIso() {
        val auto = AutoIsoSettings(50, 3200, 1 / 250.0)
        // Metered 1/50 at ISO 100 → E = 2.0
        val d = ExposureProgram.decide(ShootingMode.A, 2.0, lim, auto, null, null)
        assertEquals(1 / 250.0, d.exposureS, 1e-9)
        assertEquals(500, d.iso)
        assertEquals(0.0, d.meterEv, 1e-6)
    }

    @Test fun apertureModeUsesFasterShutterInBrightLight() {
        val d = ExposureProgram.decide(ShootingMode.A, 0.05, lim, AutoIsoSettings(50, 3200, 1 / 250.0), null, null)
        assertEquals(50, d.iso)
        assertEquals(0.001, d.exposureS, 1e-9)
    }

    @Test fun apertureModeSlowsShutterWhenIsoCeilingReached() {
        val d = ExposureProgram.decide(ShootingMode.A, 64.0, lim, AutoIsoSettings(50, 1600, 1 / 250.0), null, null)
        assertEquals(1600, d.iso)
        assertEquals(0.04, d.exposureS, 1e-9)
        assertEquals(ExposureWarning.NONE, d.warning)
    }

    @Test fun shutterPriorityWarnsWhenIsoCannotCompensate() {
        val d = ExposureProgram.decide(ShootingMode.S, 64.0, lim, AutoIsoSettings(50, 3200), 1 / 4000.0, null)
        assertEquals(3200, d.iso)
        assertEquals(ExposureWarning.UNDER, d.warning)
        assertTrue(d.meterEv < -2)
    }

    @Test fun manualModeKeepsUserSettingsAndReportsMeter() {
        val d = ExposureProgram.decide(ShootingMode.M, 2.0, lim, AutoIsoSettings(), 1 / 100.0, 400)
        assertEquals(0.01, d.exposureS, 1e-12)
        assertEquals(400, d.iso)
        assertEquals(1.0, d.meterEv, 1e-9) // 0.01*400 = 4 = 1 stop over E=2
    }

    @Test fun manualAutoIsoFollowsMeter() {
        val d = ExposureProgram.decide(ShootingMode.M, 2.0, lim, AutoIsoSettings(), 1 / 100.0, 400, manualAutoIso = true)
        assertEquals(200, d.iso)
    }

    @Test fun exposureChangesAreOneStopPerDoubling() {
        val a = ExposureProgram.decide(ShootingMode.M, 1.0, lim, AutoIsoSettings(), 1 / 100.0, 100)
        val b = ExposureProgram.decide(ShootingMode.M, 1.0, lim, AutoIsoSettings(), 1 / 50.0, 100)
        assertEquals(1.0, b.meterEv - a.meterEv, 1e-9)
    }

    @Test fun stopTablesAreThirdStopsAndRespectHardware() {
        val sh = Stops.shutterRange(1 / 8000.0, 0.5)
        assertEquals(1 / 8000.0, sh.first(), 1e-12)
        assertEquals(0.5, sh.last(), 1e-12)
        for (i in 1 until sh.size) {
            val st = Stops.log2(sh[i] / sh[i - 1])
            assertTrue("1/3 stop step $st at $i", st > 0.2 && st < 0.5)
        }
        val iso = Stops.isoRange(50, 3200)
        assertEquals(50, iso.first()); assertEquals(3200, iso.last())
        assertEquals(Stops.isoRange(42, 3500).first(), 42) // true hardware limits always reachable
        assertEquals("1/250", Stops.formatShutter(1 / 250.0))
        assertEquals("1.3\"", Stops.formatShutter(1.3))
        assertEquals("F2.8", Stops.formatAperture(2.8))
        assertEquals("+0.7", Stops.formatEv(0.6667))
    }

    // ------------------------------------------------------------------ lenses
    private val main = OpticalSource("0", 6.3, 9.8, 7.35, 4000, 3000, 1.0, 10.0, isLogical = true,
        physicalEquivalents = listOf(13.0 to 4000, 24.0 to 4000, 72.0 to 4000, 120.0 to 4000))

    @Test fun equivalentFocalLengthUsesAspectCroppedDiagonal() {
        // Full-frame-sized sensor with 50mm lens is 50mm equivalent at 3:2.
        assertEquals(50.0, LensMath.equivalentFocal(50.0, 36.0, 24.0, AspectRatio.R3_2), 1e-9)
        // A 4:3 sensor with crop factor ~4.6 at 3:2 (width limited).
        val eq = LensMath.equivalentFocal(6.3, 9.8, 7.35, AspectRatio.R3_2)
        assertEquals(6.3 * 36.0 / 9.8, eq, 0.01)
    }

    @Test fun lensPlannerPrefersLeastCropAndNeverZoomsOut() {
        val p35 = LensMath.plan(35.0, AspectRatio.R3_2, listOf(main))!!
        assertEquals(35.0 / 24.0, p35.digitalCrop, 0.01)
        assertEquals(CropClass.MODERATE, p35.cropClass)
        val p85 = LensMath.plan(85.0, AspectRatio.R3_2, listOf(main))!!
        assertEquals(85.0 / 72.0, p85.digitalCrop, 0.01)
        val p24 = LensMath.plan(24.0, AspectRatio.R3_2, listOf(main))!!
        assertEquals(CropClass.NATIVE, p24.cropClass)
        // Target wider than any source cannot be produced.
        val narrow = OpticalSource("1", 6.3, 9.8, 7.35, 4000, 3000)
        assertEquals(null, LensMath.plan(16.0, AspectRatio.R3_2, listOf(narrow)))
    }

    @Test fun heavyCropIsMarkedUnavailable() {
        val single = OpticalSource("0", 6.3, 9.8, 7.35, 4000, 3000, 1.0, 10.0)
        val p = LensMath.plan(200.0, AspectRatio.R3_2, listOf(single))!!
        assertEquals(CropClass.UNAVAILABLE, p.cropClass)
    }

    @Test fun cropRectIsCentredEvenAndAspectCorrect() {
        val r = LensMath.cropRect(4000, 3000, AspectRatio.R3_2, 1.0)
        assertEquals(4000, r[2]); assertEquals(2666, r[3]); assertEquals(0, r[0] % 2); assertEquals(0, r[1] % 2)
        val z = LensMath.cropRect(4000, 3000, AspectRatio.R1_1, 2.0)
        assertEquals(1500, z[2]); assertEquals(1500, z[3])
    }

    @Test fun whiteBalanceNeutralShiftsWithKelvin() {
        val cam = SyntheticCamera()
        val warm = cam.colorModel.neutralForWhiteBalance(3000.0, 0.0)
        val cool = cam.colorModel.neutralForWhiteBalance(7500.0, 0.0)
        // Tungsten light is red-heavy: camera neutral has more red relative to blue.
        assertTrue(warm.x / warm.z > cool.x / cool.z)
        val tintM = cam.colorModel.neutralForWhiteBalance(5500.0, 20.0)
        val tint0 = cam.colorModel.neutralForWhiteBalance(5500.0, 0.0)
        assertTrue(tintM.y > tint0.y * 0.999) // greener illuminant assumed → more green in neutral
        assertTrue(Vec3(1.0, 1.0, 1.0).x == 1.0)
    }
}
