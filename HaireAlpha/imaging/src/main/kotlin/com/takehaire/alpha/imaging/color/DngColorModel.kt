package com.takehaire.alpha.imaging.color

import kotlin.math.abs

/**
 * Camera colour model following Adobe DNG Specification 1.6, chapter 6 ("Mapping Camera Color Space
 * to CIE XYZ Space"). The inputs map one-to-one onto Android Camera2 metadata:
 *
 *  - SENSOR_COLOR_TRANSFORM1/2      → [colorMatrix1]/[colorMatrix2] (XYZ → reference camera, DNG ColorMatrix)
 *  - SENSOR_CALIBRATION_TRANSFORM1/2 → [calibration1]/[calibration2] (DNG CameraCalibration)
 *  - SENSOR_FORWARD_MATRIX1/2       → [forwardMatrix1]/[forwardMatrix2] (WB camera → XYZ D50)
 *  - SENSOR_REFERENCE_ILLUMINANT1/2 → [illuminant1]/[illuminant2] (EXIF LightSource codes)
 *
 * AnalogBalance is identity on Android. Matrices are interpolated linearly in inverse CCT (mired)
 * exactly as the DNG SDK does. No values are invented: when the second illuminant is absent the
 * first set is used throughout.
 */
class DngColorModel(
    val colorMatrix1: Mat3,
    val colorMatrix2: Mat3?,
    val forwardMatrix1: Mat3?,
    val forwardMatrix2: Mat3?,
    val calibration1: Mat3 = Mat3.IDENTITY,
    val calibration2: Mat3? = null,
    val illuminant1: Int,
    val illuminant2: Int?,
) {
    private val cct1 = illuminantCct(illuminant1)
    private val cct2 = illuminant2?.let { illuminantCct(it) }

    /** Interpolation weight for matrix set 1 at a given colour temperature. */
    fun weight1(kelvin: Double): Double {
        if (colorMatrix2 == null || cct2 == null || abs(cct1 - cct2) < 1.0) return 1.0
        val lowT = minOf(cct1, cct2)
        val highT = maxOf(cct1, cct2)
        val t = kelvin.coerceIn(lowT, highT)
        val wLow = (1.0 / t - 1.0 / highT) / (1.0 / lowT - 1.0 / highT)
        return if (cct1 <= cct2) wLow else 1.0 - wLow
    }

    private fun interp(a: Mat3, b: Mat3?, w1: Double): Mat3 {
        if (b == null) return a
        val ad = a.toDoubleArray()
        val bd = b.toDoubleArray()
        return Mat3(DoubleArray(9) { ad[it] * w1 + bd[it] * (1.0 - w1) })
    }

    /** XYZ → camera native space at a white point temperature (CC * CM, AnalogBalance = I). */
    fun xyzToCamera(kelvin: Double): Mat3 {
        val w = weight1(kelvin)
        val cm = interp(colorMatrix1, colorMatrix2, w)
        val cc = interp(calibration1, calibration2, w)
        return cc * cm
    }

    /**
     * Finds the white point chromaticity for a camera-native neutral (AsShotNeutral), iterating the
     * temperature-dependent matrix interpolation as specified by DNG.
     */
    fun neutralToXy(cameraNeutral: Vec3): DoubleArray {
        var xy = ColorMath.D50_XY.copyOf()
        repeat(30) {
            val k = ColorMath.xyToCct(xy[0], xy[1])[0]
            val xyz = xyzToCamera(k).inverse() * cameraNeutral
            val next = ColorMath.xyzToXy(xyz)
            if (abs(next[0] - xy[0]) < 1e-7 && abs(next[1] - xy[1]) < 1e-7) return next
            xy = next
        }
        return xy
    }

    /** Camera-native neutral that a white of chromaticity xy produces (normalised so max = 1). */
    fun xyToNeutral(x: Double, y: Double): Vec3 {
        val k = ColorMath.xyToCct(x, y)[0]
        val n = xyzToCamera(k) * ColorMath.xyToXYZ(x, y)
        val mx = n.maxComponent()
        return n / mx
    }

    /** Neutral for a photographic white balance setting (Kelvin + tint, Lightroom-like tint sign). */
    fun neutralForWhiteBalance(kelvin: Double, tint: Double): Vec3 {
        val xy = ColorMath.cctToXy(kelvin, tint / 3000.0)
        return xyToNeutral(xy[0], xy[1])
    }

    /**
     * Camera native RGB (not white balanced) → CIE XYZ (D50 adapted) for the given scene neutral.
     * Uses the ForwardMatrix path when available (preferred by DNG), else ColorMatrix + Bradford.
     */
    fun cameraToXyzD50(cameraNeutral: Vec3): Mat3 {
        val xy = neutralToXy(cameraNeutral)
        val kelvin = ColorMath.xyToCct(xy[0], xy[1])[0]
        val w = weight1(kelvin)
        val cc = interp(calibration1, calibration2, w)
        val fm1 = forwardMatrix1
        if (fm1 != null) {
            val fm = interp(fm1, forwardMatrix2, w)
            val ccInv = cc.inverse()
            val refNeutral = ccInv * cameraNeutral
            val d = Mat3.diag(1.0 / refNeutral.x, 1.0 / refNeutral.y, 1.0 / refNeutral.z)
            return fm * d * ccInv
        }
        val cameraToXyz = xyzToCamera(kelvin).inverse()
        val white = ColorMath.xyToXYZ(xy[0], xy[1])
        val ca = ColorMath.bradford(white, ColorMath.D50)
        // Normalise so the neutral maps to Y = 1 at full scale of the neutral's max channel.
        val m = ca * cameraToXyz
        val y = (m * cameraNeutral).y
        return m.scale(1.0 / y)
    }

    /** Camera native RGB → linear Rec.2020 (D65) for the given neutral. */
    fun cameraToRec2020(cameraNeutral: Vec3): Mat3 =
        ColorMath.XYZ_TO_REC2020 * ColorMath.D50_TO_D65 * cameraToXyzD50(cameraNeutral)

    companion object {
        /** EXIF LightSource → correlated colour temperature, values as used by the DNG SDK. */
        fun illuminantCct(code: Int): Double = when (code) {
            1, 4, 9 -> 5500.0 // daylight, flash, fine weather
            2, 14 -> 4150.0 // fluorescent, cool white fluorescent
            3 -> 2850.0 // tungsten
            10 -> 6500.0 // cloudy
            11 -> 7500.0 // shade
            12 -> 6430.0 // daylight fluorescent
            13 -> 5000.0 // day white fluorescent
            15 -> 3450.0 // white fluorescent
            17 -> 2856.0 // standard A
            18 -> 4874.0 // standard B
            19 -> 6774.0 // standard C
            20 -> 5503.0 // D55
            21 -> 6504.0 // D65
            22 -> 7504.0 // D75
            23 -> 5003.0 // D50
            24 -> 3200.0 // ISO studio tungsten
            else -> 5003.0
        }
    }
}
