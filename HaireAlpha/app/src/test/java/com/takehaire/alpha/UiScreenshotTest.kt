package com.takehaire.alpha

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.takehaire.alpha.ui.CameraScreen
import com.takehaire.alpha.ui.MenuScreen
import com.takehaire.alpha.ui.Sheet
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real Compose UI on Robolectric (no camera/GL) and saves PNGs to docs/screenshots. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: CameraViewModel

    @Before fun setUp() {
        FakeCamera.install()
        vm = CameraViewModel(ApplicationProvider.getApplicationContext<Application>())
        repeat(100) { shadowOf(Looper.getMainLooper()).idle(); if (vm.loaded.value) return; Thread.sleep(20) }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val bmp = try {
            rule.onRoot().captureToImage().asAndroidBitmap()
        } catch (e: Throwable) {
            // Fallback: draw the window's view hierarchy directly (Robolectric native graphics).
            val v = rule.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        val dir = File(System.getProperty("haire.screenshots") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.width > 100)
    }

    private fun camera(sheet: Sheet = Sheet.NONE) = rule.setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            CameraScreen(vm, 1.5f, onPlayback = {}, onMenu = {}, initialSheet = sheet)
        }
    }

    @Test fun cameraPortrait() {
        camera()
        rule.onNodeWithText("SHUTTER").assertExists()
        rule.onNodeWithText("APERTURE").assertExists()
        rule.onNodeWithText("ISO").assertExists()
        rule.onNodeWithText("MENU").assertExists()
        rule.onNodeWithText("Fn").assertExists()
        save("01_camera_portrait")
    }

    @Test @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun cameraLandscape() { camera(); save("02_camera_landscape") }

    @Test fun lookSheet() { camera(Sheet.LOOK); rule.onNodeWithText("INTENSITY").assertExists(); save("03_look_sheet") }

    @Test fun lensSheet() { camera(Sheet.LENS); rule.onNodeWithText("CAMERA BAG").assertExists(); save("04_camera_bag") }

    @Test fun fnSheet() { camera(Sheet.FN); save("05_fn_menu") }

    @Test fun modeSheet() { camera(Sheet.MODE); rule.onNodeWithText("M1").assertExists(); save("06_mode_dial") }

    @Test fun menu() {
        rule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { MenuScreen(vm, onClose = {}, onDiagnostics = {}) } }
        rule.onNodeWithText("Image format").assertExists()
        save("07_menu")
    }

    @Test fun manualModeDials() {
        vm.recallMemory(2)
        camera()
        save("08_m3_after_dark")
    }
}
