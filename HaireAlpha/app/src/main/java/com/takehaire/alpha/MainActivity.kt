package com.takehaire.alpha

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.takehaire.alpha.ui.HaireRoot
import kotlin.math.atan2

class MainActivity : ComponentActivity(), SensorEventListener {
    private val vm: CameraViewModel by viewModels()
    val permissionGranted = mutableStateOf(false)
    val permissionDeniedOnce = mutableStateOf(false)
    val levelDegrees = mutableFloatStateOf(0f)
    val uiOrientation = mutableIntStateOf(0)
    private var orientationListener: OrientationEventListener? = null
    private var sensors: SensorManager? = null

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted.value = granted
        if (!granted) permissionDeniedOnce.value = true else vm.onResumeCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        permissionGranted.value = hasCamera()
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                vm.deviceOrientation = o
                val snapped = ((o + 45) / 90 * 90) % 360
                if (snapped != uiOrientation.intValue) uiOrientation.intValue = snapped
            }
        }
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent {
            HaireRoot(
                vm = vm,
                permissionGranted = permissionGranted.value,
                permissionDenied = permissionDeniedOnce.value,
                onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
                levelDegrees = levelDegrees.floatValue,
            )
        }
        if (!permissionGranted.value) requestPermission.launch(Manifest.permission.CAMERA)
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        updateDisplayRotation()
        // Permission may have been revoked from Settings while we were away.
        permissionGranted.value = hasCamera()
        if (permissionGranted.value) vm.onResumeCamera()
        orientationListener?.enable()
        sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        vm.onPauseCamera()
        orientationListener?.disable()
        sensors?.unregisterListener(this)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDisplayRotation()
    }

    @Suppress("DEPRECATION")
    private fun updateDisplayRotation() {
        val r = (if (android.os.Build.VERSION.SDK_INT >= 30) display?.rotation else windowManager.defaultDisplay.rotation) ?: 0
        vm.displayRotation = r * 90
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && permissionGranted.value) {
            if (event?.repeatCount == 0 && vm.onVolumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
            if (vm.settings.value.volumeKey != com.takehaire.alpha.settings.VolumeKeyAction.OFF) return true
        }
        if (keyCode == KeyEvent.KEYCODE_CAMERA && permissionGranted.value) { vm.shutter(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // Roll relative to the current display orientation, for the electronic level.
        val x = e.values[0]; val y = e.values[1]
        val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        val disp = vm.displayRotation
        var r = roll + disp
        while (r > 180) r -= 360
        while (r < -180) r += 360
        levelDegrees.floatValue = r
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
