package com.takehaire.alpha

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import android.util.SizeF
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowCameraCharacteristics

/**
 * Registers a SIMULATED back camera with Robolectric (plausible flagship-like values, not measured
 * from any real device) so view-model logic can be exercised without hardware.
 */
object FakeCamera {
    fun install() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ch = ShadowCameraCharacteristics.newCameraCharacteristics()
        val s = shadowOf(ch)
        s.set(CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK)
        s.set(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS, floatArrayOf(6.3f))
        s.set(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES, floatArrayOf(1.7f))
        s.set(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE, SizeF(9.8f, 7.35f))
        s.set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, Size(4000, 3000))
        s.set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, Rect(0, 0, 4000, 3000))
        s.set(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE, Range(50, 3200))
        s.set(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE, Range(20_000L, 500_000_000L))
        s.set(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE, Range(1f, 10f))
        s.set(CameraCharacteristics.SENSOR_ORIENTATION, 90)
        s.set(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE, Range(-12, 12))
        s.set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, intArrayOf(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING,
        ))
        s.set(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES, intArrayOf(CameraMetadata.CONTROL_AF_MODE_AUTO, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
        shadowOf(cm).addCamera("0", ch)
    }
}
