package com.takehaire.alpha.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takehaire.alpha.CameraViewModel
import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.exposure.Stops
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.lens.CropClass
import com.takehaire.alpha.imaging.lens.LensKind
import com.takehaire.alpha.imaging.look.LookCategory
import com.takehaire.alpha.settings.DispMode
import com.takehaire.alpha.settings.FnItem
import com.takehaire.alpha.settings.FocusArea
import com.takehaire.alpha.settings.FocusMode
import com.takehaire.alpha.settings.ImageFormat
import com.takehaire.alpha.settings.WbMode

@Composable
fun SheetHost(vm: CameraViewModel, sheet: Sheet, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000)).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Hc.Charcoal).pointerInput(Unit) { detectTapGestures { } }.padding(14.dp),
        ) {
            when (sheet) {
                Sheet.MODE -> ModeSheet(vm, onDismiss)
                Sheet.LENS -> LensSheet(vm, onDismiss)
                Sheet.LOOK -> LookSheet(vm)
                Sheet.FN -> FnSheet(vm)
                Sheet.SHUTTER -> ValueWheel("SHUTTER", vm.shutterValues().map { Stops.formatShutter(it) }, Stops.nearestIndex(vm.shutterValues(), if (vm.current.mode in listOf(ShootingMode.A, ShootingMode.P, ShootingMode.AUTO)) vm.current.minShutterS else vm.current.shutterS)) { i ->
                    val v = vm.shutterValues()[i]
                    if (vm.current.mode in listOf(ShootingMode.A, ShootingMode.P, ShootingMode.AUTO)) vm.updatePreset { it.copy(minShutterS = v) } else vm.updatePreset { it.copy(shutterS = v) }
                }
                Sheet.APERTURE -> {
                    ValueWheel("VIRTUAL APERTURE", vm.apertureValues().map { Stops.formatAperture(it) }, Stops.nearestIndex(vm.apertureValues(), vm.current.virtualAperture)) { i ->
                        vm.updatePreset { it.copy(virtualAperture = vm.apertureValues()[i]) }
                    }
                    val bokehOn = vm.settings.collectAsState().value.current.bokeh
                    Row(Modifier.padding(top = 8.dp)) {
                        Chip(if (bokehOn) "VIRTUAL BOKEH: ON" else "VIRTUAL BOKEH: OFF", highlighted = bokehOn) { vm.setBokeh(!bokehOn) }
                    }
                    Text(
                        "The phone's lens has a fixed physical aperture; exposure always uses it. With virtual bokeh on, the virtual aperture sets how much the background is blurred (experimental, added when the photo is saved; the viewfinder does not show it). If no clear subject is found, the photo is saved without blur.",
                        style = Hc.label.copy(fontSize = 11.sp, letterSpacing = 0.sp), modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Sheet.ISO -> {
                    val values = vm.isoValues()
                    val labels = listOf("AUTO") + values.map { it.toString() }
                    val sel = if (vm.current.isoAuto) 0 else Stops.nearestIsoIndex(values, vm.current.iso) + 1
                    ValueWheel("ISO", labels, sel) { i -> if (i == 0) vm.setIsoAuto(true) else vm.updatePreset { it.copy(isoAuto = false, iso = values[i - 1]) } }
                    Text("AUTO ISO range ${vm.current.isoMin}–${vm.current.isoMax} (MENU › Exposure)", style = Hc.label, modifier = Modifier.padding(top = 8.dp))
                }
                Sheet.NONE -> {}
            }
        }
    }
}

@Composable
private fun SheetTitle(t: String) {
    Text(t, style = Hc.label.copy(fontSize = 12.sp), modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
fun ValueWheel(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var sel by remember { mutableStateOf(selected) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    SheetTitle(title)
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels.size) { i ->
            Box(
                Modifier.height(52.dp).width(68.dp).clip(RoundedCornerShape(4.dp)).background(if (i == sel) Hc.Panel else Hc.Charcoal)
                    .border(1.dp, if (i == sel) Hc.Amber else Hc.Line, RoundedCornerShape(4.dp)).tap { sel = i; onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(labels[i], style = Hc.small.copy(color = if (i == sel) Hc.Amber else Hc.WarmWhite, fontSize = 14.sp)) }
        }
    }
}

@Composable
private fun ModeSheet(vm: CameraViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    SheetTitle("SHOOTING MODE")
    val modes = listOf(ShootingMode.AUTO, ShootingMode.P, ShootingMode.A, ShootingMode.S, ShootingMode.M)
    val hints = mapOf(
        ShootingMode.AUTO to "Everything automatic", ShootingMode.P to "Auto exposure, your colour and EV",
        ShootingMode.A to "Aperture priority · Auto ISO with minimum shutter", ShootingMode.S to "You set the shutter speed",
        ShootingMode.M to "You set shutter and ISO",
    )
    for (m in modes) {
        ModeRow(m.label, hints[m]!!, s.memorySlot == null && s.current.mode == m) { vm.setMode(m); onDismiss() }
    }
    for (i in 0..2) {
        val mem = s.memories[i]
        ModeRow("M${i + 1}", "${mem.name.substringAfter(' ')} · ${mem.mode.label} · ${Assets.look(mem.lookId).name}", s.memorySlot == i) { vm.recallMemory(i); onDismiss() }
    }
}

@Composable
private fun ModeRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).tap(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Hc.value.copy(color = if (selected) Hc.Amber else Hc.WarmWhite), modifier = Modifier.width(64.dp))
        Text(hint, style = Hc.small.copy(color = Hc.Dim))
    }
}

@Composable
private fun LensSheet(vm: CameraViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    SheetTitle("CAMERA BAG")
    if (s.frontCamera) {
        Text("Front camera in use — virtual lenses apply to the rear cameras.", style = Hc.small, modifier = Modifier.padding(bottom = 8.dp))
        Chip("Use rear camera", highlighted = true) { vm.update { it.copy(frontCamera = false) } }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val favs = s.favoriteLenses
        val sorted = vm.lenses.sortedWith(compareBy({ it.id !in favs }, { !it.core }, { it.minFocal }))
        items(sorted) { l ->
            val status = vm.lensStatus(l)
            val enabled = status != null && status != CropClass.UNAVAILABLE
            val mounted = s.current.lensId == l.id
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(4.dp)).background(if (mounted) Hc.Panel else Hc.Charcoal)
                    .border(1.dp, if (mounted) Hc.Amber else Hc.Line, RoundedCornerShape(4.dp))
                    .tap(enabled = enabled) { vm.mountLens(l.id); onDismiss() }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = Hc.small.copy(color = if (enabled) Hc.WarmWhite else Hc.Dim))
                    Text(
                        (if (l.kind == LensKind.ZOOM) "Zoom" else "Prime · fixed focal length") + " · " + (status?.label ?: "unknown") +
                            (if (l.note.isNotEmpty()) " · framing only" else ""),
                        style = Hc.label.copy(letterSpacing = 0.sp),
                    )
                }
                Text(
                    if (l.id in favs) "★" else "☆", style = Hc.value.copy(color = if (l.id in favs) Hc.Amber else Hc.Dim),
                    modifier = Modifier.tap {
                        vm.update { st ->
                            val f = st.favoriteLenses
                            st.copy(favoriteLenses = if (l.id in f) f - l.id else (f + l.id).takeLast(5))
                        }
                    }.padding(8.dp),
                )
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "Virtual lenses set framing by choosing the best physical camera and cropping as little as possible. " +
                    "◦ moderate crop, • substantial crop. Cropping cannot add optical resolution, and focal length alone does not change perspective — your position does.",
                style = Hc.label.copy(fontSize = 11.sp, letterSpacing = 0.sp),
            )
            Spacer(Modifier.height(6.dp))
            Chip("Front camera") { vm.update { it.copy(frontCamera = true) }; onDismiss() }
        }
    }
}

@Composable
private fun LookSheet(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val thumbs by vm.lookThumbs.collectAsState()
    var cat by remember { mutableStateOf(Assets.looks.looks.firstOrNull { it.id == s.current.lookId }?.category ?: LookCategory.CREATIVE) }
    var favOnly by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.requestLookThumbnails() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        val cats = listOf(LookCategory.CREATIVE, LookCategory.FILM, LookCategory.SIGNATURE) + if (s.recipes.isNotEmpty()) listOf(LookCategory.CUSTOM) else emptyList()
        for (c in cats) Chip(c.name, highlighted = c == cat && !favOnly) { cat = c; favOnly = false }
        Chip("★ FAVOURITES", highlighted = favOnly) { favOnly = !favOnly }
    }
    Spacer(Modifier.height(10.dp))
    val looks = if (favOnly) s.favoriteLooks.map { vm.lookFor(it) }
    else if (cat == LookCategory.CUSTOM) s.recipes.map { vm.lookFor(it.id) }
    else Assets.looks.looks.filter { it.category == cat }
    LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 420.dp)) {
        items(looks) { l ->
            val selected = s.current.lookId == l.id
            Column(
                Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, if (selected) Hc.Amber else Hc.Line, RoundedCornerShape(4.dp))
                    .tap { vm.updatePreset { it.copy(lookId = l.id) } }.padding(4.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.5f).background(Hc.Panel)) {
                    thumbs[l.id]?.let { Image(it.asImageBitmap(), l.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    val fav = l.id in s.favoriteLooks
                    Text(if (fav) "★" else "☆", style = Hc.small.copy(color = if (fav) Hc.Amber else Hc.WarmWhite), modifier = Modifier.align(Alignment.TopEnd).tap {
                        vm.update { st -> st.copy(favoriteLooks = if (l.id in st.favoriteLooks) st.favoriteLooks - l.id else (st.favoriteLooks + l.id).takeLast(5)) }
                    }.padding(4.dp))
                }
                Text(l.name.replace(" INSPIRED", "*"), style = Hc.label.copy(color = if (selected) Hc.Amber else Hc.WarmWhite, letterSpacing = 0.3.sp), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("INTENSITY", style = Hc.label, modifier = Modifier.width(76.dp))
        Slider(
            value = s.current.lookIntensity.toFloat(), onValueChange = { v -> vm.updatePreset { it.copy(lookIntensity = (v * 20).toInt() / 20.0) } },
            colors = SliderDefaults.colors(thumbColor = Hc.Amber, activeTrackColor = Hc.Amber, inactiveTrackColor = Hc.Line), modifier = Modifier.weight(1f),
        )
        Text("${(s.current.lookIntensity * 100).toInt()}%", style = Hc.small, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
    Text("* original looks inspired by the character of these films; not reproductions.", style = Hc.label.copy(fontSize = 9.sp, letterSpacing = 0.sp))
}

@Composable
private fun FnSheet(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    var customizing by remember { mutableStateOf<Int?>(null) }
    SheetTitle(if (customizing != null) "CHOOSE ITEM FOR SLOT ${customizing!! + 1}" else "Fn  ·  long-press a tile to change it")
    val slot = customizing
    if (slot != null) {
        LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(FnItem.entries) { item ->
                Chip(item.label, highlighted = s.fnSlots.getOrNull(slot) == item) {
                    vm.update { st -> st.copy(fnSlots = st.fnSlots.toMutableList().also { l -> if (slot < l.size) l[slot] = item else l.add(item) }) }
                    customizing = null
                }
            }
        }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(s.fnSlots.size) { i ->
            val item = s.fnSlots[i]
            Column(
                Modifier.height(64.dp).clip(RoundedCornerShape(4.dp)).background(Hc.Panel).border(1.dp, Hc.Line, RoundedCornerShape(4.dp))
                    .pointerInput(item) { detectTapGestures(onTap = { fnAction(vm, item) }, onLongPress = { customizing = i }) }.padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.label.uppercase(), style = Hc.label.copy(fontSize = 8.sp, letterSpacing = 0.4.sp), maxLines = 1, textAlign = TextAlign.Center)
                Text(fnValue(vm, item), style = Hc.small.copy(fontSize = 12.sp), maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
    val wbItem = s.fnSlots.contains(FnItem.WHITE_BALANCE)
    if (wbItem && (s.current.wbMode == WbMode.KELVIN || s.current.wbMode == WbMode.CUSTOM)) {
        Spacer(Modifier.height(10.dp))
        KelvinControls(vm)
    }
}

@Composable
fun KelvinControls(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${s.current.kelvin}K", style = Hc.small, modifier = Modifier.width(64.dp))
        Slider(
            value = s.current.kelvin.toFloat(), valueRange = 2500f..10000f,
            onValueChange = { v -> vm.updatePreset { it.copy(kelvin = (v / 50).toInt() * 50) } },
            colors = SliderDefaults.colors(thumbColor = Hc.Amber, activeTrackColor = Hc.Amber, inactiveTrackColor = Hc.Line), modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("TINT ${s.current.tint}", style = Hc.small, modifier = Modifier.width(64.dp))
        Slider(
            value = s.current.tint.toFloat(), valueRange = -30f..30f,
            onValueChange = { v -> vm.updatePreset { it.copy(tint = v.toInt()) } },
            colors = SliderDefaults.colors(thumbColor = Hc.Amber, activeTrackColor = Hc.Amber, inactiveTrackColor = Hc.Line), modifier = Modifier.weight(1f),
        )
    }
}

fun fnValue(vm: CameraViewModel, item: FnItem): String {
    val s = vm.settings.value
    val p = s.current
    return when (item) {
        FnItem.FOCUS_MODE -> p.focusMode.label
        FnItem.WHITE_BALANCE -> if (p.wbMode == WbMode.KELVIN) "${p.kelvin}K" else p.wbMode.label
        FnItem.EXPOSURE_COMP -> Stops.formatEv(p.exposureComp)
        FnItem.PROCESSING -> p.processing.label.substringBefore(' ').take(8)
        FnItem.FORMAT -> p.format.label
        FnItem.ASPECT -> p.aspect.label
        FnItem.BOKEH -> if (p.bokeh) "ON" else "OFF"
        FnItem.HISTOGRAM -> if (s.histogram) "ON" else "OFF"
        FnItem.GRID -> if (s.grid) "ON" else "OFF"
        FnItem.MEMORY -> s.memorySlot?.let { "M${it + 1}" } ?: "—"
        FnItem.FOCUS_AREA -> s.focusArea.label
        FnItem.PEAKING -> if (s.peaking) "ON" else "OFF"
        FnItem.ZEBRA -> if (s.zebras) "${s.zebraLevel}" else "OFF"
        FnItem.DISP -> s.disp.label
        FnItem.SETTING_EFFECT -> if (s.settingEffect) "ON" else "OFF"
        FnItem.CAMERA -> if (s.frontCamera) "FRONT" else "REAR"
        FnItem.LEVEL -> if (s.level) "ON" else "OFF"
        FnItem.LOCK -> if (s.controlLock) "LOCKED" else "OFF"
    }
}

private fun <T> next(list: List<T>, cur: T): T = list[(list.indexOf(cur) + 1) % list.size]

fun fnAction(vm: CameraViewModel, item: FnItem) {
    when (item) {
        FnItem.FOCUS_MODE -> vm.updatePreset { it.copy(focusMode = next(FocusMode.entries, it.focusMode)) }
        FnItem.WHITE_BALANCE -> vm.updatePreset { it.copy(wbMode = next(listOf(WbMode.AUTO, WbMode.DAYLIGHT, WbMode.CLOUDY, WbMode.SHADE, WbMode.TUNGSTEN, WbMode.FLUORESCENT, WbMode.KELVIN), it.wbMode)) }
        FnItem.EXPOSURE_COMP -> vm.updatePreset { it.copy(exposureComp = 0.0) }
        FnItem.PROCESSING -> vm.updatePreset { it.copy(processing = next(ProcessingMode.entries, it.processing)) }
        FnItem.FORMAT -> vm.updatePreset { it.copy(format = next(ImageFormat.entries, it.format)) }
        FnItem.ASPECT -> vm.updatePreset { it.copy(aspect = next(AspectRatio.entries, it.aspect)) }
        FnItem.BOKEH -> vm.setBokeh(!vm.current.bokeh)
        FnItem.HISTOGRAM -> vm.update { it.copy(histogram = !it.histogram) }
        FnItem.GRID -> vm.update { it.copy(grid = !it.grid) }
        FnItem.MEMORY -> vm.recallMemory(((vm.settings.value.memorySlot ?: -1) + 1) % 3)
        FnItem.FOCUS_AREA -> vm.update { it.copy(focusArea = next(FocusArea.entries, it.focusArea)) }
        FnItem.PEAKING -> vm.update { it.copy(peaking = !it.peaking) }
        FnItem.ZEBRA -> vm.update { it.copy(zebras = !it.zebras) }
        FnItem.DISP -> vm.update { it.copy(disp = next(DispMode.entries, it.disp)) }
        FnItem.SETTING_EFFECT -> vm.update { it.copy(settingEffect = !it.settingEffect) }
        FnItem.CAMERA -> vm.update { it.copy(frontCamera = !it.frontCamera) }
        FnItem.LEVEL -> vm.update { it.copy(level = !it.level) }
        FnItem.LOCK -> vm.update { it.copy(controlLock = !it.controlLock) }
    }
}
