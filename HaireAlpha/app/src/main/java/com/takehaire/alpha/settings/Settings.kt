package com.takehaire.alpha.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.look.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class ImageFormat(val label: String) { JPEG("JPEG"), RAW_JPEG("RAW+JPEG"), RAW("RAW") }
@Serializable enum class WbMode(val label: String) { AUTO("AWB"), DAYLIGHT("Daylight"), CLOUDY("Cloudy"), SHADE("Shade"), TUNGSTEN("Tungsten"), FLUORESCENT("Fluor."), KELVIN("K"), CUSTOM("Custom") }
@Serializable enum class FocusMode(val label: String) { AF_S("AF-S"), AF_C("AF-C"), MF("MF") }
@Serializable enum class FocusArea(val label: String) { WIDE("Wide"), CENTER("Center"), SPOT("Flexible spot"), FACE("Face") }
@Serializable enum class DispMode(val label: String) { CLEAN("Clean"), ESSENTIAL("Essential"), PRO("Professional"), COMPOSE("Composition") }
@Serializable enum class VolumeKeyAction(val label: String) { SHUTTER("Shutter"), EXPOSURE("Exposure comp."), ZOOM("Zoom"), OFF("Disabled") }
@Serializable enum class PathwayPreference(val label: String) { AUTO("Auto"), RAW("RAW development"), PROCESSED("Processed (YUV)") }
@Serializable enum class FnItem(val label: String) {
    FOCUS_MODE("Focus mode"), WHITE_BALANCE("White balance"), EXPOSURE_COMP("Exposure comp."), PROCESSING("Processing"),
    FORMAT("Image format"), ASPECT("Aspect"), BOKEH("Virtual bokeh"), HISTOGRAM("Histogram"), GRID("Grid"),
    MEMORY("Memory recall"), FOCUS_AREA("Focus area"), PEAKING("Peaking"), ZEBRA("Zebras"), DISP("Display"),
    SETTING_EFFECT("Setting effect"), CAMERA("Front/Rear"), LEVEL("Level"), LOCK("Control lock"),
}

/** Settings that define a photograph; these are what Memory Recall stores. */
@Serializable
data class ShootingPreset(
    val name: String,
    val mode: ShootingMode = ShootingMode.A,
    val lensId: String = "p40_25",
    val zoomFocal: Double? = null,
    val virtualAperture: Double = 2.8,
    val bokeh: Boolean = false,
    val isoAuto: Boolean = true,
    val iso: Int = 100,
    val shutterS: Double = 1 / 250.0,
    val minShutterS: Double = 1 / 250.0,
    val isoMin: Int = 50,
    val isoMax: Int = 3200,
    val exposureComp: Double = 0.0,
    val wbMode: WbMode = WbMode.AUTO,
    val kelvin: Int = 5500,
    val tint: Int = 0,
    val lookId: String = "alpha_natural",
    val lookIntensity: Double = 1.0,
    val focusMode: FocusMode = FocusMode.AF_S,
    val processing: ProcessingMode = ProcessingMode.NATURAL,
    val format: ImageFormat = ImageFormat.JPEG,
    val aspect: AspectRatio = AspectRatio.R3_2,
)

@Serializable
data class AppSettings(
    val schema: Int = 1,
    val current: ShootingPreset = Defaults.factory,
    /** Which slot (0..2) the mode dial is on when M1–M3 is selected; null = P/A/S/M/AUTO. */
    val memorySlot: Int? = null,
    val memories: List<ShootingPreset> = Defaults.memories,
    val wbShiftAB: Double = 0.0, // amber(+)/blue(−)
    val wbShiftGM: Double = 0.0, // magenta(+)/green(−)
    val focusArea: FocusArea = FocusArea.WIDE,
    val disp: DispMode = DispMode.ESSENTIAL,
    val grid: Boolean = false,
    val level: Boolean = false,
    val histogram: Boolean = false,
    val zebras: Boolean = false,
    val zebraLevel: Int = 95,
    val peaking: Boolean = true,
    val settingEffect: Boolean = true,
    val manualAutoIso: Boolean = false,
    val dialReversed: Boolean = false,
    val controlLock: Boolean = false,
    val volumeKey: VolumeKeyAction = VolumeKeyAction.SHUTTER,
    val autoReview: Boolean = false,
    val pathway: PathwayPreference = PathwayPreference.AUTO,
    val frontCamera: Boolean = false,
    val favoriteLenses: List<String> = listOf("p24_14", "p35_14", "p40_25", "p50_12", "p85_14"),
    val favoriteLooks: List<String> = listOf("alpha_natural", "forever", "portra400", "classic_chrome", "trix400"),
    val fnSlots: List<FnItem> = Defaults.fn,
    val myMenu: List<String> = listOf("look_intensity", "wb", "aspect", "format", "memory_save"),
    val recipes: List<Recipe> = emptyList(),
    /** Learned RAW exposure normalisation per camera id (stops), see docs/IMAGING.md. */
    val learnedBaseline: Map<String, Double> = emptyMap(),
    val lastLensByFocal: Map<String, Double> = emptyMap(),
    /** True once the factory lens has been adapted to this phone's real cameras (first launch). */
    val hardwareAdapted: Boolean = false,
)

object Defaults {
    /** Factory first-launch configuration (spec §44). */
    val factory = ShootingPreset(name = "Factory")

    val memories = listOf(
        ShootingPreset(
            name = "M1 EVERYDAY", mode = ShootingMode.A, lensId = "p40_25", virtualAperture = 2.8,
            isoAuto = true, minShutterS = 1 / 250.0, wbMode = WbMode.AUTO, lookId = "forever", format = ImageFormat.JPEG,
        ),
        ShootingPreset(
            name = "M2 STREET", mode = ShootingMode.S, lensId = "p35_14", shutterS = 1 / 500.0, isoAuto = true,
            lookId = "classic_chrome", focusMode = FocusMode.AF_C,
        ),
        ShootingPreset(
            name = "M3 AFTER DARK", mode = ShootingMode.M, lensId = "p35_14", shutterS = 1 / 60.0, isoAuto = false, iso = 1600,
            lookId = "cinestill800t", wbMode = WbMode.KELVIN, kelvin = 3200,
        ),
    )

    val fn = listOf(
        FnItem.FOCUS_MODE, FnItem.WHITE_BALANCE, FnItem.EXPOSURE_COMP, FnItem.PROCESSING, FnItem.FORMAT,
        FnItem.ASPECT, FnItem.BOKEH, FnItem.HISTOGRAM, FnItem.GRID, FnItem.MEMORY,
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "haire_settings")

/**
 * Persists [AppSettings] as one JSON document. Unknown/missing fields fall back to defaults and a
 * corrupted document resets to factory settings instead of crashing the camera.
 */
class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    var lastLoadWasCorrupt = false
        private set

    suspend fun load(): AppSettings {
        val raw = try {
            context.dataStore.data.first()[key]
        } catch (_: Exception) {
            null
        } ?: return AppSettings()
        return decode(raw)
    }

    fun decode(raw: String): AppSettings = try {
        sanitize(json.decodeFromString(AppSettings.serializer(), raw))
    } catch (_: Exception) {
        lastLoadWasCorrupt = true
        AppSettings()
    }

    fun encode(s: AppSettings): String = json.encodeToString(AppSettings.serializer(), s)

    suspend fun save(s: AppSettings) {
        try {
            context.dataStore.edit { it[key] = encode(s) }
        } catch (_: Exception) {
            // Storage full or I/O error: keep running with in-memory settings.
        }
    }

    companion object {
        /** Repairs out-of-range values that could come from older versions or manual edits. */
        fun sanitize(s: AppSettings): AppSettings {
            fun fix(p: ShootingPreset) = p.copy(
                virtualAperture = p.virtualAperture.coerceIn(0.95, 22.0),
                iso = p.iso.coerceIn(25, 409600),
                shutterS = p.shutterS.coerceIn(1 / 16000.0, 60.0),
                minShutterS = p.minShutterS.coerceIn(1 / 8000.0, 1.0),
                isoMin = p.isoMin.coerceIn(25, 6400),
                isoMax = p.isoMax.coerceIn(p.isoMin.coerceIn(25, 6400), 409600),
                exposureComp = p.exposureComp.coerceIn(-5.0, 5.0),
                kelvin = p.kelvin.coerceIn(2000, 12000),
                tint = p.tint.coerceIn(-100, 100),
                lookIntensity = p.lookIntensity.coerceIn(0.0, 1.0),
            )
            val mem = (s.memories + Defaults.memories).take(3).map(::fix)
            return s.copy(
                current = fix(s.current),
                memories = mem,
                memorySlot = s.memorySlot?.takeIf { it in 0..2 },
                favoriteLenses = s.favoriteLenses.distinct().take(5),
                favoriteLooks = s.favoriteLooks.distinct().take(5),
                fnSlots = s.fnSlots.take(12).ifEmpty { Defaults.fn },
                wbShiftAB = s.wbShiftAB.coerceIn(-9.0, 9.0),
                wbShiftGM = s.wbShiftGM.coerceIn(-9.0, 9.0),
                zebraLevel = s.zebraLevel.coerceIn(70, 100),
            )
        }
    }
}
