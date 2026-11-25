package com.takehaire.alpha.imaging.exposure

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Serializable
enum class ShootingMode(val label: String) {
    AUTO("AUTO"), P("P"), A("A"), S("S"), M("M");
}

/** Conventional photographic values in 1/3-stop increments. */
object Stops {
    /** Shutter speeds in seconds, fastest first. */
    val SHUTTER: List<Double> = listOf(
        1 / 8000.0, 1 / 6400.0, 1 / 5000.0, 1 / 4000.0, 1 / 3200.0, 1 / 2500.0, 1 / 2000.0, 1 / 1600.0,
        1 / 1250.0, 1 / 1000.0, 1 / 800.0, 1 / 640.0, 1 / 500.0, 1 / 400.0, 1 / 320.0, 1 / 250.0,
        1 / 200.0, 1 / 160.0, 1 / 125.0, 1 / 100.0, 1 / 80.0, 1 / 60.0, 1 / 50.0, 1 / 40.0,
        1 / 30.0, 1 / 25.0, 1 / 20.0, 1 / 15.0, 1 / 13.0, 1 / 10.0, 1 / 8.0, 1 / 6.0, 1 / 5.0, 1 / 4.0,
        0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0,
        20.0, 25.0, 30.0,
    )

    val ISO: List<Int> = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600,
        2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200,
    )

    val APERTURE: List<Double> = listOf(
        0.95, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0,
        9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0,
    )

    fun shutterRange(minS: Double, maxS: Double): List<Double> =
        SHUTTER.filter { it >= minS * 0.97 && it <= maxS * 1.03 }

    fun isoRange(minIso: Int, maxIso: Int): List<Int> = ISO.filter { it >= minIso && it <= maxIso }.let { list ->
        // Always include the true hardware limits so the full range stays reachable.
        (listOf(minIso) + list + listOf(maxIso)).distinct().sorted()
    }

    fun nearestIndex(values: List<Double>, v: Double): Int =
        values.indices.minByOrNull { abs(ln(values[it]) - ln(v)) } ?: 0

    fun nearestIsoIndex(values: List<Int>, v: Int): Int =
        values.indices.minByOrNull { abs(ln(values[it].toDouble()) - ln(v.toDouble())) } ?: 0

    fun formatShutter(s: Double): String = when {
        s >= 0.29 -> {
            val r = (s * 10).roundToInt() / 10.0
            if (r == r.toInt().toDouble()) "${r.toInt()}\"" else "$r\""
        }
        else -> "1/${(1.0 / s).roundToInt()}"
    }

    fun formatAperture(n: Double): String = if (n == n.toInt().toDouble() && n >= 10) "F${n.toInt()}" else "F%.1f".format(java.util.Locale.ROOT, n)

    fun formatEv(ev: Double): String {
        val thirds = (ev * 3).roundToInt()
        if (thirds == 0) return "±0.0"
        val sign = if (thirds > 0) "+" else "−"
        val v = abs(thirds) / 3.0
        return sign + "%.1f".format(java.util.Locale.ROOT, v)
    }

    fun log2(v: Double) = ln(v) / ln(2.0)
}

/** What the camera hardware can do (from CameraCharacteristics). */
data class ExposureLimits(
    val minExposureS: Double,
    val maxExposureS: Double,
    val minIso: Int,
    val maxIso: Int,
    /** Max ISO that is analog on the sensor; beyond is digital gain. */
    val maxAnalogIso: Int = maxIso,
    val manualSensor: Boolean = true,
)

/** Photographer preferences for automatic exposure. */
@Serializable
data class AutoIsoSettings(
    val minIso: Int = 50,
    val maxIso: Int = 3200,
    /** Slowest shutter the program prefers before raising ISO, seconds. */
    val minShutterS: Double = 1 / 250.0,
)

enum class ExposureWarning { NONE, UNDER, OVER }

data class ExposureDecision(
    val exposureS: Double,
    val iso: Int,
    val warning: ExposureWarning,
    /** Difference between chosen exposure and the metered target, stops (+ = brighter). */
    val meterEv: Double,
)

/**
 * The exposure program. The camera's own auto-exposure is used as the light meter: it reports the
 * exposure product E = t × ISO (× post-RAW boost) that it considers correct (already including
 * exposure compensation). This program then decides how to split E into a real shutter speed and a
 * real ISO according to the shooting mode — sent to the sensor with AE disabled, so the photograph
 * genuinely uses those settings.
 *
 * Physical aperture is fixed on most phones; when a variable aperture exists the product is scaled
 * by (N_capture / N_meter)².
 */
object ExposureProgram {

    fun decide(
        mode: ShootingMode,
        meteredProduct: Double,
        limits: ExposureLimits,
        auto: AutoIsoSettings,
        userShutterS: Double?,
        userIso: Int?,
        /** P-mode program shift in stops (+ = faster shutter). */
        programShift: Double = 0.0,
        /** Manual mode with Auto ISO enabled. */
        manualAutoIso: Boolean = false,
        /** P/A/S/AUTO: false = the photographer fixed ISO at [userIso]. */
        isoAuto: Boolean = true,
    ): ExposureDecision {
        val e = meteredProduct.coerceAtLeast(1e-9)
        val isoLo = maxOf(auto.minIso, limits.minIso)
        val isoHi = minOf(auto.maxIso, limits.maxIso).coerceAtLeast(isoLo)
        if (!isoAuto && userIso != null && mode != ShootingMode.M && mode != ShootingMode.AUTO) {
            val iso = userIso.coerceIn(limits.minIso, limits.maxIso)
            if (mode == ShootingMode.S) {
                val t = (userShutterS ?: auto.minShutterS).coerceIn(limits.minExposureS, limits.maxExposureS)
                val ev = Stops.log2(t * iso / e)
                val w = if (ev < -0.17) ExposureWarning.UNDER else if (ev > 0.17) ExposureWarning.OVER else ExposureWarning.NONE
                return ExposureDecision(t, iso, w, ev)
            }
            val tIdeal = e / iso
            val t = tIdeal.coerceIn(limits.minExposureS, limits.maxExposureS)
            return ExposureDecision(t, iso, warningFor(tIdeal, t), Stops.log2(t * iso / e))
        }
        return when (mode) {
            ShootingMode.AUTO, ShootingMode.A -> autoIso(e, auto.minShutterS, isoLo, isoHi, limits)
            ShootingMode.P -> {
                val base = autoIso(e, (auto.minShutterS * 2.0.pow(-programShift)), isoLo, isoHi, limits)
                base
            }
            ShootingMode.S -> {
                val t = (userShutterS ?: auto.minShutterS).coerceIn(limits.minExposureS, limits.maxExposureS)
                val isoIdeal = e / t
                val iso = isoIdeal.coerceIn(isoLo.toDouble(), isoHi.toDouble())
                val warn = warningFor(isoIdeal, iso)
                ExposureDecision(t, iso.roundToInt(), warn, Stops.log2(t * iso / e))
            }
            ShootingMode.M -> {
                val t = (userShutterS ?: auto.minShutterS).coerceIn(limits.minExposureS, limits.maxExposureS)
                if (manualAutoIso) {
                    val isoIdeal = e / t
                    val iso = isoIdeal.coerceIn(limits.minIso.toDouble(), limits.maxIso.toDouble())
                    ExposureDecision(t, iso.roundToInt(), warningFor(isoIdeal, iso), Stops.log2(t * iso / e))
                } else {
                    val iso = (userIso ?: 100).coerceIn(limits.minIso, limits.maxIso)
                    ExposureDecision(t, iso, ExposureWarning.NONE, Stops.log2(t * iso / e))
                }
            }
        }
    }

    private fun warningFor(ideal: Double, actual: Double): ExposureWarning = when {
        ideal > actual * 1.12 -> ExposureWarning.UNDER
        ideal < actual / 1.12 -> ExposureWarning.OVER
        else -> ExposureWarning.NONE
    }

    /**
     * Auto ISO with a minimum shutter speed target (Sony-like): hold the target shutter and vary ISO;
     * when ISO reaches its floor use faster shutter; when it reaches its ceiling allow slower shutter.
     */
    private fun autoIso(e: Double, minShutter: Double, isoLo: Int, isoHi: Int, lim: ExposureLimits): ExposureDecision {
        val tTarget = minShutter.coerceIn(lim.minExposureS, lim.maxExposureS)
        var t = tTarget
        var iso = e / t
        if (iso < isoLo) {
            iso = isoLo.toDouble()
            t = e / iso
        } else if (iso > isoHi) {
            iso = isoHi.toDouble()
            t = e / iso
        }
        var warn = ExposureWarning.NONE
        if (t < lim.minExposureS) {
            t = lim.minExposureS; warn = ExposureWarning.OVER
        }
        if (t > lim.maxExposureS) {
            t = lim.maxExposureS; warn = ExposureWarning.UNDER
        }
        return ExposureDecision(t, iso.roundToInt(), warn, Stops.log2(t * iso / e))
    }

    /** Exposure value at ISO 100 for display: EV100 = log2(N² / t) − log2(ISO/100). */
    fun ev100(aperture: Double, exposureS: Double, iso: Int): Double =
        Stops.log2(aperture * aperture / exposureS) - Stops.log2(iso / 100.0)
}
