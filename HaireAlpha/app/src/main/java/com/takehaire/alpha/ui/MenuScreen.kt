package com.takehaire.alpha.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takehaire.alpha.CameraViewModel
import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.exposure.Stops
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.look.Recipe
import com.takehaire.alpha.settings.DispMode
import com.takehaire.alpha.settings.FocusArea
import com.takehaire.alpha.settings.FocusMode
import com.takehaire.alpha.settings.ImageFormat
import com.takehaire.alpha.settings.PathwayPreference
import com.takehaire.alpha.settings.VolumeKeyAction
import com.takehaire.alpha.settings.WbMode
import kotlin.math.roundToInt

private enum class Tab(val label: String) { SHOOT("Shooting"), EXPOSURE("Exposure"), COLOR("Color"), FOCUS("Focus"), DISPLAY("Display"), PLAYBACK("Playback"), SETUP("Setup"), MY("My Menu") }

@Composable
fun MenuScreen(vm: CameraViewModel, onClose: () -> Unit, onDiagnostics: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.SHOOT) }
    Column(Modifier.fillMaxSize().background(Hc.Charcoal).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("MENU", style = Hc.value, modifier = Modifier.weight(1f))
            Chip("CLOSE", onClick = onClose)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in Tab.entries) Chip(t.label, highlighted = t == tab) { tab = t }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                when (tab) {
                    Tab.SHOOT -> ShootingTab(vm)
                    Tab.EXPOSURE -> ExposureTab(vm)
                    Tab.COLOR -> ColorTab(vm)
                    Tab.FOCUS -> FocusTab(vm)
                    Tab.DISPLAY -> DisplayTab(vm)
                    Tab.PLAYBACK -> PlaybackTab(vm)
                    Tab.SETUP -> SetupTab(vm, onDiagnostics)
                    Tab.MY -> MyMenuTab(vm)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun Section(title: String) {
    Text(title.uppercase(), style = Hc.label, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = Hc.body)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) Chip(name(o), highlighted = o == selected) { onSelect(o) }
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, hint: String? = null, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(if (hint == null) 52.dp else 64.dp).tap { onToggle(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Hc.body)
            if (hint != null) Text(hint, style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
        }
        Box(Modifier.size(44.dp, 24.dp).clip(RoundedCornerShape(12.dp)).background(if (value) Hc.Amber else Hc.Line), contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(18.dp).clip(RoundedCornerShape(9.dp)).background(Hc.WarmWhite))
        }
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row { Text(label, style = Hc.body, modifier = Modifier.weight(1f)); Text(display, style = Hc.small) }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Hc.Amber, activeTrackColor = Hc.Amber, inactiveTrackColor = Hc.Line))
    }
}

@Composable
private fun ShootingTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val p = s.current
    ChoiceRow("Image format", ImageFormat.entries, p.format, { it.label }) { f -> vm.updatePreset { it.copy(format = f) } }
    if (p.format != ImageFormat.JPEG && vm.controller?.info()?.raw != true)
        Text("RAW is not offered by this camera; JPEG will be saved.", style = Hc.label.copy(color = Hc.Warn, letterSpacing = 0.sp))
    ChoiceRow("Aspect ratio", AspectRatio.entries, p.aspect, { it.label }) { a -> vm.updatePreset { it.copy(aspect = a) } }
    ChoiceRow("Processing", ProcessingMode.entries, p.processing, { it.label }) { m -> vm.updatePreset { it.copy(processing = m) } }
    Text(p.processing.blurb, style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
    ToggleRow("Virtual bokeh (experimental)", p.bokeh, "Blurs the background by the virtual aperture when the photo is saved. Skipped when no clear subject is found.") { vm.setBokeh(it) }
    Section("Memory recall")
    for (i in 0..2) {
        val m = s.memories[i]
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = Hc.body)
                Text("${m.mode.label} · ${vm.lens(m.lensId).name} · ${Assets.look(m.lookId).name}", style = Hc.label.copy(letterSpacing = 0.sp))
            }
            Chip("Recall") { vm.recallMemory(i) }
            Spacer(Modifier.width(6.dp))
            Chip("Save current") { vm.saveMemory(i) }
        }
    }
    Section("Reset")
    Chip("Reset to factory settings") { vm.resetToFactory() }
}

@Composable
private fun ExposureTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val p = s.current
    val isoVals = vm.isoValues()
    SliderRow("Auto ISO minimum", isoVals.indexOf(isoVals.minBy { kotlin.math.abs(it - p.isoMin) }).toFloat(), 0f..(isoVals.size - 1).toFloat(), p.isoMin.toString()) { v ->
        vm.updatePreset { it.copy(isoMin = isoVals[v.roundToInt()]) }
    }
    SliderRow("Auto ISO maximum", isoVals.indexOf(isoVals.minBy { kotlin.math.abs(it - p.isoMax) }).toFloat(), 0f..(isoVals.size - 1).toFloat(), p.isoMax.toString()) { v ->
        vm.updatePreset { it.copy(isoMax = isoVals[v.roundToInt()]) }
    }
    val sh = vm.shutterValues().filter { it <= 1.0 }
    SliderRow("Minimum shutter speed (A / P / AUTO)", Stops.nearestIndex(sh, p.minShutterS).toFloat(), 0f..(sh.size - 1).toFloat(), Stops.formatShutter(p.minShutterS)) { v ->
        vm.updatePreset { it.copy(minShutterS = sh[v.roundToInt()]) }
    }
    ToggleRow("Auto ISO in M mode", s.manualAutoIso, "Shutter is yours; ISO follows the meter.") { b -> vm.update { it.copy(manualAutoIso = b) } }
    ToggleRow("Setting Effect", s.settingEffect, "Viewfinder shows your exposure and look. Off: plain bright preview.") { b -> vm.update { it.copy(settingEffect = b) } }
}

@Composable
private fun ColorTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val p = s.current
    val ctx = LocalContext.current
    ChoiceRow("White balance", WbMode.entries.filter { it != WbMode.CUSTOM }, p.wbMode, { it.label }) { w -> vm.updatePreset { it.copy(wbMode = w) } }
    if (p.wbMode == WbMode.KELVIN) KelvinControls(vm)
    vm.activeLook.filmWhiteBalanceK?.let {
        if (p.wbMode == WbMode.AUTO) Text("${vm.activeLook.name} is a tungsten-balanced stock: AWB is replaced by ${it.toInt()}K, like loading that film.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
    }
    Section("White balance shift")
    WbShiftGrid(s.wbShiftAB, s.wbShiftGM) { ab, gm -> vm.update { it.copy(wbShiftAB = ab, wbShiftGM = gm) } }
    Section("Look intensity")
    SliderRow("Intensity", p.lookIntensity.toFloat(), 0f..1f, "${(p.lookIntensity * 100).roundToInt()}%") { v -> vm.updatePreset { it.copy(lookIntensity = (v * 20).roundToInt() / 20.0) } }
    Section("Custom recipes")
    RecipeEditor(vm)
    val cubePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importCube(it, p.lookId)?.let { err -> vm.notify(err) } ?: vm.notify("LUT imported as a custom look.") }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u -> try { ctx.contentResolver.openOutputStream(u)?.use { it.write(vm.exportRecipes().toByteArray()) }; vm.notify("Recipes exported.") } catch (_: Exception) { vm.notify("Export failed.") } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> val t = try { ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } } catch (_: Exception) { null }
            vm.notify(t?.let { vm.importRecipes(it) } ?: if (t == null) "File could not be read." else "Recipes imported.") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Chip("Import .cube LUT") { cubePicker.launch(arrayOf("*/*")) }
        Chip("Export recipes") { exportLauncher.launch("haire-recipes.json") }
        Chip("Import recipes") { importLauncher.launch(arrayOf("application/json", "*/*")) }
    }
    Text(".cube LUTs must be 3D, domain 0–1, and are applied as sRGB in → sRGB out after the base look.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
}

@Composable
fun WbShiftGrid(ab: Double, gm: Double, onChange: (Double, Double) -> Unit) {
    Column {
        for (row in 4 downTo -4) Row {
            for (col in -4..4) {
                val sel = (ab.roundToInt() == col && gm.roundToInt() == row)
                val c = Color(
                    (0.5f + col * 0.05f - row * 0.02f).coerceIn(0f, 1f), (0.5f - row * 0.05f).coerceIn(0f, 1f), (0.5f - col * 0.05f + row * 0.02f).coerceIn(0f, 1f),
                )
                Box(Modifier.size(30.dp).padding(1.dp).background(if (sel) Hc.Amber else c.copy(alpha = 0.5f)).tap { onChange(col.toDouble(), row.toDouble()) })
            }
        }
        Text("A/B ${ab.roundToInt()}   G/M ${gm.roundToInt()}   (B ← → A,  G ↓ ↑ M)", style = Hc.label)
        Chip("Reset shift") { onChange(0.0, 0.0) }
    }
}

@Composable
private fun RecipeEditor(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val p = s.current
    val existing = s.recipes.firstOrNull { it.id == p.lookId }
    var r by remember(p.lookId) { mutableStateOf(existing ?: Recipe("r" + System.currentTimeMillis(), "MY " + vm.activeLook.name.take(14), p.lookId)) }
    Text(if (existing != null) "Editing ${existing.name}" else "Create a recipe from ${vm.activeLook.name}", style = Hc.small)
    @Composable
    fun sl(label: String, v: Double, range: ClosedFloatingPointRange<Float> = -1f..1f, set: (Double) -> Recipe) =
        SliderRow(label, v.toFloat(), range, "%+.2f".format(java.util.Locale.US, v)) { r = set((it * 20).roundToInt() / 20.0) }
    sl("Contrast", r.contrast) { r.copy(contrast = it) }
    sl("Highlights", r.highlights) { r.copy(highlights = it) }
    sl("Shadows", r.shadows) { r.copy(shadows = it) }
    sl("Saturation", r.saturation) { r.copy(saturation = it) }
    sl("Temperature", r.temperature) { r.copy(temperature = it) }
    sl("Tint", r.tint) { r.copy(tint = it) }
    sl("Clarity", r.clarity) { r.copy(clarity = it) }
    sl("Sharpness", r.sharpness) { r.copy(sharpness = it) }
    sl("Vignette", r.vignette) { r.copy(vignette = it) }
    sl("Grain", r.grain, 0f..1f) { r.copy(grain = it) }
    sl("Fade", r.fade, 0f..1f) { r.copy(fade = it) }
    sl("Halation", r.halation, 0f..1f) { r.copy(halation = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Chip("Save & use", highlighted = true) { vm.saveRecipe(r); vm.updatePreset { it.copy(lookId = r.id) } }
        if (existing != null) {
            Chip("Duplicate") { val d = r.copy(id = "r" + System.currentTimeMillis(), name = (r.name + " 2").take(24)); vm.saveRecipe(d); vm.updatePreset { it.copy(lookId = d.id) } }
            Chip("Rename") { val d = r.copy(name = "RECIPE ${s.recipes.size}"); r = d; vm.saveRecipe(d) }
            Chip("Delete") { vm.deleteRecipe(existing.id) }
        }
    }
}

@Composable
private fun FocusTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    ChoiceRow("Focus mode", FocusMode.entries, s.current.focusMode, { it.label }) { f -> vm.updatePreset { it.copy(focusMode = f) } }
    ChoiceRow("Focus area", FocusArea.entries, s.focusArea, { it.label }) { f -> vm.update { it.copy(focusArea = f) } }
    Text("Touch the viewfinder to focus there; double-tap to return to the focus area. Touch focus never changes exposure. Face detection uses the camera's own detector where available.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
    ToggleRow("Focus peaking (MF)", s.peaking) { b -> vm.update { it.copy(peaking = b) } }
    Text("Focus magnification: the viewfinder magnifies 3× while you turn the MF control.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
}

@Composable
private fun DisplayTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    ChoiceRow("DISP", DispMode.entries, s.disp, { it.label }) { d -> vm.update { it.copy(disp = d) } }
    ToggleRow("Grid", s.grid) { b -> vm.update { it.copy(grid = b) } }
    ToggleRow("Electronic level", s.level) { b -> vm.update { it.copy(level = b) } }
    ToggleRow("Histogram", s.histogram) { b -> vm.update { it.copy(histogram = b) } }
    ToggleRow("Zebras", s.zebras) { b -> vm.update { it.copy(zebras = b) } }
    if (s.zebras) SliderRow("Zebra level", s.zebraLevel.toFloat(), 70f..100f, "${s.zebraLevel}") { v -> vm.update { it.copy(zebraLevel = v.roundToInt()) } }
    ToggleRow("Reverse dial direction", s.dialReversed) { b -> vm.update { it.copy(dialReversed = b) } }
    ToggleRow("Control lock", s.controlLock, "Prevents accidental dial changes.") { b -> vm.update { it.copy(controlLock = b) } }
    ChoiceRow("Volume buttons", VolumeKeyAction.entries, s.volumeKey, { it.label }) { v -> vm.update { it.copy(volumeKey = v) } }
}

@Composable
private fun PlaybackTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    ToggleRow("Auto review after capture", s.autoReview) { b -> vm.update { it.copy(autoReview = b) } }
    Text("Photos are saved to DCIM/HAIRE Alpha and appear in Samsung Gallery as the HAIRE Alpha album.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
}

@Composable
private fun SetupTab(vm: CameraViewModel, onDiagnostics: () -> Unit) {
    val s by vm.settings.collectAsState()
    ChoiceRow("Capture pathway", PathwayPreference.entries, s.pathway, { it.label }) { v -> vm.update { it.copy(pathway = v) } }
    Text("Auto: RAW development when the camera provides RAW with lens-shading data, otherwise the processed YUV pathway with edge enhancement off.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
    ToggleRow("Front camera", s.frontCamera) { b -> vm.update { it.copy(frontCamera = b) } }
    Spacer(Modifier.height(10.dp))
    Chip("Diagnostics", onClick = onDiagnostics)
    Section("About")
    Text("HAIRE Alpha ${com.takehaire.alpha.BuildConfig.VERSION_NAME}. An original camera; not affiliated with Sony, Fujifilm, Leica or any film manufacturer. Looks marked “Inspired” are original interpretations.", style = Hc.label.copy(letterSpacing = 0.sp, fontSize = 11.sp))
}

@Composable
private fun MyMenuTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val p = s.current
    SliderRow("Look intensity", p.lookIntensity.toFloat(), 0f..1f, "${(p.lookIntensity * 100).roundToInt()}%") { v -> vm.updatePreset { it.copy(lookIntensity = (v * 20).roundToInt() / 20.0) } }
    ChoiceRow("White balance", WbMode.entries.filter { it != WbMode.CUSTOM }, p.wbMode, { it.label }) { w -> vm.updatePreset { it.copy(wbMode = w) } }
    ChoiceRow("Aspect ratio", AspectRatio.entries, p.aspect, { it.label }) { a -> vm.updatePreset { it.copy(aspect = a) } }
    ChoiceRow("Image format", ImageFormat.entries, p.format, { it.label }) { f -> vm.updatePreset { it.copy(format = f) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..2) Chip("Save to M${i + 1}") { vm.saveMemory(i) }
    }
}

@Suppress("unused")
private fun shareText(ctx: android.content.Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
}
