package com.takehaire.alpha

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.takehaire.alpha.camera.BitmapSink
import com.takehaire.alpha.camera.PhotoStore
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.lens.CropClass
import com.takehaire.alpha.imaging.process.Orientation
import com.takehaire.alpha.settings.AppSettings
import com.takehaire.alpha.settings.Defaults
import com.takehaire.alpha.settings.FnItem
import com.takehaire.alpha.settings.FocusMode
import com.takehaire.alpha.settings.ImageFormat
import com.takehaire.alpha.settings.SettingsRepository
import com.takehaire.alpha.settings.WbMode
import com.takehaire.alpha.ui.fnAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLogicTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = FakeCamera.install()

    private fun vm(): CameraViewModel {
        val vm = CameraViewModel(app)
        repeat(50) { shadowOf(Looper.getMainLooper()).idle(); if (vm.loaded.value) return vm; Thread.sleep(20) }
        assertTrue("view model did not finish loading", vm.loaded.value)
        return vm
    }

    @Test fun factoryDefaultsMatchSpecification() {
        val p = Defaults.factory
        assertEquals("alpha_natural", p.lookId)
        assertEquals(ProcessingMode.NATURAL, p.processing)
        assertEquals(ShootingMode.A, p.mode)
        assertTrue(p.isoAuto)
        assertEquals(1 / 250.0, p.minShutterS, 1e-12)
        assertEquals(WbMode.AUTO, p.wbMode)
        assertEquals("p40_25", p.lensId)
        assertEquals(ImageFormat.JPEG, p.format)
        assertEquals(AspectRatio.R3_2, p.aspect)
        assertFalse(p.bokeh)
        assertEquals(1.0, p.lookIntensity, 0.0)
    }

    @Test fun memoryRecallPresetsMatchSpecification() {
        val (m1, m2, m3) = Defaults.memories
        assertEquals(ShootingMode.A, m1.mode); assertEquals("p40_25", m1.lensId); assertEquals("forever", m1.lookId); assertEquals(1 / 250.0, m1.minShutterS, 0.0)
        assertEquals(ShootingMode.S, m2.mode); assertEquals("p35_14", m2.lensId); assertEquals(1 / 500.0, m2.shutterS, 0.0); assertEquals("classic_chrome", m2.lookId); assertEquals(FocusMode.AF_C, m2.focusMode)
        assertEquals(ShootingMode.M, m3.mode); assertEquals(1 / 60.0, m3.shutterS, 0.0); assertEquals("cinestill800t", m3.lookId); assertEquals(WbMode.KELVIN, m3.wbMode)
        assertEquals(10, Defaults.fn.size)
    }

    @Test fun corruptedSettingsFallBackToFactory() {
        val repo = SettingsRepository(app)
        val s = repo.decode("{ this is not json")
        assertEquals(AppSettings(), s)
        assertTrue(repo.lastLoadWasCorrupt)
    }

    @Test fun settingsRoundTripAndOutOfRangeValuesAreRepaired() {
        val repo = SettingsRepository(app)
        val s = AppSettings(current = Defaults.factory.copy(iso = 999999, lookIntensity = 7.0, kelvin = 50), grid = true)
        val back = repo.decode(repo.encode(s))
        assertTrue(back.grid)
        assertEquals(409600, back.current.iso)
        assertEquals(1.0, back.current.lookIntensity, 0.0)
        assertEquals(2000, back.current.kelvin)
        // Unknown future fields are ignored instead of crashing.
        val withExtra = repo.encode(s).replaceFirst("{", "{\"futureField\":42,")
        assertTrue(repo.decode(withExtra).grid)
    }

    @Test fun discoveryFindsSimulatedCameraAndPlansLenses() {
        val vm = vm()
        val caps = vm.caps.value
        assertNotNull(caps)
        assertEquals(1, caps!!.back.size)
        val eq = caps.back[0].equivalentFocal(AspectRatio.R3_2)!!
        assertEquals(6.3 * 36 / 9.8, eq, 0.2)
        // Factory 40mm would need a substantial (1.73x) crop on this camera, so first launch mounts 35mm (spec §44).
        assertEquals(CropClass.SUBSTANTIAL, vm.lensStatus(vm.lens("p40_25")))
        assertEquals("p35_14", vm.current.lensId)
        assertEquals(35.0 / eq, vm.plan()!!.digitalCrop, 0.02)
        assertTrue(vm.settings.value.hardwareAdapted)
        // The simulated camera is ~23mm: 24mm is native, 20mm is wider than any optics → unavailable, never faked.
        assertEquals(CropClass.NATIVE, vm.lensStatus(vm.lens("p24_14")))
        assertEquals(CropClass.UNAVAILABLE, vm.lensStatus(vm.lens("p20_18")))
        assertNotEquals(CropClass.UNAVAILABLE, vm.lensStatus(vm.lens("p50_12")))
    }

    @Test fun modeSwitchingKeepsSettingsAndMemoryRecallRestores() {
        val vm = vm()
        vm.updatePreset { it.copy(lookId = "portra400", exposureComp = 1.0) }
        vm.setMode(ShootingMode.S)
        assertEquals(ShootingMode.S, vm.current.mode)
        assertEquals("portra400", vm.current.lookId)
        assertEquals(1.0, vm.current.exposureComp, 0.0)
        vm.recallMemory(1)
        assertEquals(ShootingMode.S, vm.current.mode)
        assertEquals("classic_chrome", vm.current.lookId)
        assertEquals(1, vm.settings.value.memorySlot)
        vm.setMode(ShootingMode.A)
        assertEquals(null, vm.settings.value.memorySlot)
        vm.saveMemory(2)
        assertEquals(ShootingMode.A, vm.settings.value.memories[2].mode)
    }

    @Test fun primeLensLocksFocalAndZoomLensIsConstrained() {
        val vm = vm()
        vm.mountLens("p35_14")
        vm.setZoomFocal(80.0)
        assertEquals(35.0, vm.focalFor(vm.lens()), 0.0)
        vm.mountLens("z24_70")
        vm.setZoomFocal(300.0)
        assertEquals(70.0, vm.focalFor(vm.lens()), 0.0)
        vm.setZoomFocal(10.0)
        assertEquals(24.0, vm.focalFor(vm.lens()), 0.0)
    }

    @Test fun exposureCompensationStepsInThirdsWithinHardwareRange() {
        val vm = vm()
        repeat(20) { vm.stepExposureComp(1) }
        assertTrue(vm.current.exposureComp <= 5.0)
        vm.updatePreset { it.copy(exposureComp = 0.0) }
        vm.stepExposureComp(1)
        assertEquals(1 / 3.0, vm.current.exposureComp, 1e-9)
    }

    @Test fun everyFnActionIsSafe() {
        val vm = vm()
        for (item in FnItem.entries) repeat(3) { fnAction(vm, item) }
        assertTrue(vm.settings.value.fnSlots.isNotEmpty())
    }

    @Test fun tungstenFilmStockFixesWhiteBalanceUnderAwbOnly() {
        val vm = vm()
        vm.updatePreset { it.copy(lookId = "cinestill800t", wbMode = WbMode.AUTO) }
        assertEquals(3200 to 0, vm.fixedKelvin())
        vm.updatePreset { it.copy(wbMode = WbMode.DAYLIGHT) }
        assertEquals(null, vm.fixedKelvin())
        vm.updatePreset { it.copy(lookId = "alpha_natural", wbMode = WbMode.KELVIN, kelvin = 4300, tint = 5) }
        assertEquals(4300 to 5, vm.fixedKelvin())
    }

    @Test fun resetToFactoryKeepsLearnedCalibration() {
        val vm = vm()
        vm.update { it.copy(learnedBaseline = mapOf("0" to -0.7), grid = true) }
        vm.resetToFactory()
        assertFalse(vm.settings.value.grid)
        assertEquals(-0.7, vm.settings.value.learnedBaseline["0"]!!, 0.0)
    }

    @Test fun bitmapSinkRotatesLikeReferenceOrientation() {
        val w = 6; val h = 4
        for (rot in listOf(0, 90, 180, 270)) {
            val outW = if (rot % 180 == 0) w else h
            val outH = if (rot % 180 == 0) h else w
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val sink = BitmapSink(bmp, w, h, rot)
            // Two stripes of two rows each.
            for (r0 in listOf(0, 2)) {
                val px = IntArray(w * 2) { i -> 0xFF000000.toInt() or ((r0 + i / w) shl 8) or (i % w) }
                sink.write(r0, w, 2, px)
            }
            for (y in 0 until h) for (x in 0 until w) {
                val (dx, dy) = Orientation.map(x, y, w, h, rot)
                val p = bmp.getPixel(dx, dy)
                assertEquals("rot $rot ($x,$y)", (y shl 8) or x, p and 0xFFFF)
            }
        }
    }

    @Test fun photoNamesAreUniqueWithinTheSameMillisecond() {
        val store = PhotoStore(app)
        val a = store.newBaseName(1_700_000_000_000)
        val b = store.newBaseName(1_700_000_000_000)
        assertTrue(a.startsWith("HAIRE_"))
        assertNotEquals(a, b)
    }

    @Test fun recipeImportExportRoundTrips() {
        val vm = vm()
        vm.saveRecipe(com.takehaire.alpha.imaging.look.Recipe("r1", "MY LOOK", "forever", contrast = 0.3))
        val json = vm.exportRecipes()
        vm.deleteRecipe("r1")
        assertTrue(vm.settings.value.recipes.isEmpty())
        assertEquals(null, vm.importRecipes(json))
        assertEquals(0.3, vm.settings.value.recipes.single().contrast, 0.0)
        assertNotNull(vm.importRecipes("not json"))
    }
}
