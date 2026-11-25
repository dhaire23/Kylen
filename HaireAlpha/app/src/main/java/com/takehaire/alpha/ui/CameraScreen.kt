package com.takehaire.alpha.ui

import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.takehaire.alpha.CameraViewModel
import com.takehaire.alpha.camera.CamState
import com.takehaire.alpha.gl.PreviewRenderer
import com.takehaire.alpha.imaging.exposure.ExposureWarning
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.exposure.Stops
import com.takehaire.alpha.imaging.lens.CropClass
import com.takehaire.alpha.imaging.lens.LensKind
import com.takehaire.alpha.settings.DispMode
import com.takehaire.alpha.settings.FocusMode
import kotlinx.coroutines.delay

enum class Screen { CAMERA, PLAYBACK, MENU, DIAGNOSTICS }
enum class Sheet { NONE, MODE, LENS, LOOK, FN, SHUTTER, APERTURE, ISO }

@Composable
fun HaireRoot(
    vm: CameraViewModel,
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    levelDegrees: Float,
) {
    var screen by remember { mutableStateOf(Screen.CAMERA) }
    val review by vm.reviewRequest.collectAsState()
    LaunchedEffect(review) { if (review > 0) screen = Screen.PLAYBACK }
    Box(Modifier.fillMaxSize().background(Hc.Black)) {
        if (!permissionGranted) {
            PermissionScreen(permissionDenied, onRequestPermission)
        } else {
            CameraScreen(vm, levelDegrees, onPlayback = { screen = Screen.PLAYBACK }, onMenu = { screen = Screen.MENU })
            when (screen) {
                Screen.PLAYBACK -> PlaybackScreen(vm, onClose = { screen = Screen.CAMERA })
                Screen.MENU -> MenuScreen(vm, onClose = { screen = Screen.CAMERA }, onDiagnostics = { screen = Screen.DIAGNOSTICS })
                Screen.DIAGNOSTICS -> DiagnosticsScreen(vm, onClose = { screen = Screen.MENU })
                Screen.CAMERA -> {}
            }
        }
        NoticeBar(vm)
    }
    androidx.activity.compose.BackHandler(enabled = screen != Screen.CAMERA) {
        screen = if (screen == Screen.DIAGNOSTICS) Screen.MENU else Screen.CAMERA
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("HAIRE Alpha", style = Hc.value.copy(fontSize = 26.sp))
        Spacer(Modifier.height(12.dp))
        Text("To take photographs, allow access to the camera.", style = Hc.body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Chip(if (denied) "Open settings" else "Allow camera", highlighted = true) {
            if (denied) ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            else onRequest()
        }
        if (denied) {
            Spacer(Modifier.height(12.dp))
            Chip("Ask again", onClick = onRequest)
        }
    }
}

@Composable
private fun NoticeBar(vm: CameraViewModel) {
    val notice by vm.notice.collectAsState()
    val n = notice ?: return
    LaunchedEffect(n.id) { delay(3800); vm.clearNotice() }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            n.text, style = Hc.small, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(4.dp)).background(Hc.Charcoal)
                .border(1.dp, Hc.Line, RoundedCornerShape(4.dp)).tap { vm.clearNotice() }.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Display values computed for the shooting UI. */
data class ExposureView(
    val shutter: String, val shutterAuto: Boolean, val shutterSub: String?,
    val aperture: String, val apertureSub: String,
    val iso: String, val isoAuto: Boolean, val isoSub: String?,
    val meterEv: Double, val meterMode: Boolean, val warning: Boolean,
)

@Composable
fun rememberExposureView(vm: CameraViewModel): ExposureView {
    val s by vm.settings.collectAsState()
    val live by (vm.controller?.live ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.takehaire.alpha.camera.LiveResult()) }).collectAsState()
    val stats by vm.stats.collectAsState()
    @Suppress("UNUSED_EXPRESSION") live; stats
    val p = s.current
    val d = vm.decision()
    val autoShutter = p.mode == ShootingMode.A || p.mode == ShootingMode.P || p.mode == ShootingMode.AUTO
    val shutter = if (autoShutter) d?.let { Stops.formatShutter(it.exposureS) } ?: "--" else Stops.formatShutter(p.shutterS)
    val isoAutoShown = p.isoAuto && p.mode != ShootingMode.M || (p.mode == ShootingMode.M && s.manualAutoIso)
    val iso = if (isoAutoShown) "AUTO" else p.iso.toString()
    val isoSub = if (isoAutoShown) (d?.iso?.let { "ISO $it" } ?: "${p.isoMin}–${p.isoMax}") else null
    val phys = vm.controller?.info()?.apertures?.firstOrNull()
    val apSub = if (p.bokeh) "BOKEH ON" else "VIRTUAL" + (phys?.let { " · f/%.1f".format(java.util.Locale.US, it) } ?: "")
    return ExposureView(
        shutter = shutter, shutterAuto = autoShutter, shutterSub = if (autoShutter) "MIN ${Stops.formatShutter(p.minShutterS)}" else null,
        aperture = Stops.formatAperture(p.virtualAperture), apertureSub = apSub,
        iso = iso, isoAuto = isoAutoShown, isoSub = isoSub,
        meterEv = if (p.mode == ShootingMode.M) (d?.meterEv ?: 0.0) else p.exposureComp,
        meterMode = p.mode == ShootingMode.M,
        warning = d?.warning?.let { it != ExposureWarning.NONE } ?: false,
    )
}

@Composable
fun CameraScreen(vm: CameraViewModel, levelDegrees: Float, onPlayback: () -> Unit, onMenu: () -> Unit, initialSheet: Sheet = Sheet.NONE) {
    val settings by vm.settings.collectAsState()
    var sheet by remember { mutableStateOf(initialSheet) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Hc.Black).safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val aspect = settings.current.aspect.value.toFloat()
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    ViewfinderArea(vm, aspect, landscape = true, levelDegrees)
                }
                Column(Modifier.width(312.dp).fillMaxHeight().background(Hc.Charcoal).padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    TopBar(vm, onMenu, { sheet = Sheet.MODE }, compact = true)
                    DialsBlock(vm, { sheet = it }, vertical = false)
                    BottomRow(vm, onPlayback, { sheet = it }, compact = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(vm, onMenu, { sheet = Sheet.MODE }, compact = false)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ViewfinderArea(vm, 1f / aspect, landscape = false, levelDegrees)
                }
                Column(Modifier.fillMaxWidth().background(Hc.Charcoal).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    DialsBlock(vm, { sheet = it }, vertical = false)
                    Spacer(Modifier.height(8.dp))
                    BottomRow(vm, onPlayback, { sheet = it }, compact = false)
                }
            }
        }
        if (sheet != Sheet.NONE) SheetHost(vm, sheet, onDismiss = { sheet = Sheet.NONE })
    }
}

@Composable
private fun TopBar(vm: CameraViewModel, onMenu: () -> Unit, onMode: () -> Unit, compact: Boolean) {
    val s by vm.settings.collectAsState()
    val state by (vm.controller?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<CamState>(CamState.Idle) }).collectAsState()
    val p = s.current
    val modeLabel = s.memorySlot?.let { "M${it + 1}" } ?: p.mode.label
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = if (compact) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(modeLabel, highlighted = true, onClick = onMode)
        if (!compact) {
            val pathway = (state as? CamState.Ready)?.pathway?.name ?: ""
            Column(Modifier.weight(1f)) {
                Text(vm.activeLook.name, style = Hc.small, maxLines = 1)
                Text("${p.processing.label} · ${p.format.label} · ${p.aspect.label}${if (pathway.isNotEmpty()) " · $pathway" else ""}", style = Hc.label, maxLines = 1)
            }
        } else Spacer(Modifier.weight(1f))
        Chip("DISP") {
            vm.update { it.copy(disp = DispMode.entries[(it.disp.ordinal + 1) % DispMode.entries.size]) }
        }
        Chip("MENU", onClick = onMenu)
    }
}

@Composable
private fun DialsBlock(vm: CameraViewModel, openSheet: (Sheet) -> Unit, vertical: Boolean) {
    val s by vm.settings.collectAsState()
    val ev = rememberExposureView(vm)
    val p = s.current
    val locked = s.controlLock
    val lens = vm.lens()
    Column {
        if (lens.kind == LensKind.ZOOM && !s.frontCamera) {
            ZoomRing(lens.minFocal, lens.maxFocal, vm.focalFor(lens), { vm.setZoomFocal(it) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        if (p.focusMode == FocusMode.MF) {
            ManualFocusSlider(vm)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExposureDial(
                "SHUTTER", ev.shutter, Modifier.weight(1f), auto = ev.shutterAuto, warning = ev.warning && p.mode == ShootingMode.S,
                locked = locked, reversed = s.dialReversed, subLabel = ev.shutterSub,
                onStep = { d ->
                    if (ev.shutterAuto) vm.updatePreset { q ->
                        val v = vm.shutterValues().filter { it <= 1.0 }
                        q.copy(minShutterS = v[(Stops.nearestIndex(v, q.minShutterS) - d).coerceIn(0, v.size - 1)])
                    } else vm.stepShutter(-d)
                },
                onTap = { openSheet(Sheet.SHUTTER) },
            )
            ExposureDial(
                "APERTURE", ev.aperture, Modifier.weight(1f), active = true, auto = !p.bokeh, locked = locked,
                reversed = s.dialReversed, subLabel = ev.apertureSub, onStep = { vm.stepAperture(it) }, onTap = { openSheet(Sheet.APERTURE) },
            )
            ExposureDial(
                "ISO", ev.iso, Modifier.weight(1f), auto = ev.isoAuto, warning = ev.warning && p.mode != ShootingMode.M,
                locked = locked, reversed = s.dialReversed, subLabel = ev.isoSub, onStep = { vm.stepIso(it) }, onTap = { openSheet(Sheet.ISO) },
            )
        }
        Spacer(Modifier.height(4.dp))
        ExposureMeter(ev.meterEv, ev.meterMode, ev.warning, Modifier.fillMaxWidth(), locked) { vm.stepExposureComp(it) }
    }
}

@Composable
private fun ManualFocusSlider(vm: CameraViewModel) {
    val info = vm.controller?.info()
    val max = (info?.minFocusDistance ?: 10f).coerceAtLeast(0.1f)
    var v by remember { mutableStateOf(vm.manualFocus) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("MF", style = Hc.label, modifier = Modifier.width(28.dp))
        Slider(
            value = v, valueRange = 0f..max,
            onValueChange = { v = it; vm.applyManualFocus(it); vm.magnifying = true },
            onValueChangeFinished = { vm.magnifying = false },
            colors = SliderDefaults.colors(thumbColor = Hc.Amber, activeTrackColor = Hc.Amber, inactiveTrackColor = Hc.Line),
            modifier = Modifier.weight(1f),
        )
        Text(if (v <= 0.01f) "∞" else "%.2fm".format(java.util.Locale.US, 1f / v), style = Hc.small, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun BottomRow(vm: CameraViewModel, onPlayback: () -> Unit, openSheet: (Sheet) -> Unit, compact: Boolean) {
    val s by vm.settings.collectAsState()
    val last by vm.last.collectAsState()
    val pending by vm.pending.collectAsState()
    val state by (vm.controller?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<CamState>(CamState.Idle) }).collectAsState()
    val lens = vm.lens()
    val plan = vm.plan()
    val lensText = if (s.frontCamera) "FRONT" else "${vm.focalFor(lens).toInt()}mm"
    val cropMark = when (plan?.cropClass) {
        CropClass.NATIVE -> ""
        CropClass.MODERATE -> " ◦"
        CropClass.SUBSTANTIAL -> " •"
        CropClass.UNAVAILABLE -> " ×"
        null -> ""
    }
    @Composable
    fun thumb() {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(Hc.Panel).border(1.dp, Hc.Line, RoundedCornerShape(4.dp)).tap(onClick = onPlayback),
            contentAlignment = Alignment.Center,
        ) {
            val t = last?.thumb
            if (t != null) Image(t.asImageBitmap(), "Last photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("▶", style = Hc.small)
        }
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(lensText + cropMark, Modifier.weight(1f)) { openSheet(Sheet.LENS) }
                LookChip(vm.activeLook.name, Modifier.weight(1f)) { openSheet(Sheet.LOOK) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                thumb()
                ShutterButton(pending, state is CamState.Ready, onPress = { vm.shutter() })
                Chip("Fn") { openSheet(Sheet.FN) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            thumb()
            Chip(lensText + cropMark, Modifier.width(84.dp)) { openSheet(Sheet.LENS) }
            ShutterButton(pending, state is CamState.Ready, onPress = { vm.shutter() })
            LookChip(vm.activeLook.name, Modifier.width(96.dp)) { openSheet(Sheet.LOOK) }
            Chip("Fn") { openSheet(Sheet.FN) }
        }
    }
}

@Composable
private fun ViewfinderArea(vm: CameraViewModel, viewAspect: Float, landscape: Boolean, levelDegrees: Float) {
    val s by vm.settings.collectAsState()
    val stats by vm.stats.collectAsState()
    val state by (vm.controller?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<CamState>(CamState.Idle) }).collectAsState()
    var focusPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w: Dp
        val h: Dp
        if (maxWidth / maxHeight > viewAspect) { h = maxHeight; w = maxHeight * viewAspect } else { w = maxWidth; h = maxWidth / viewAspect }
        Box(Modifier.size(w, h).background(Hc.Black)) {
            if (!LocalInspectionMode.current) GlViewfinder(vm, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF3A4048), androidx.compose.ui.graphics.Color(0xFF6B6157), androidx.compose.ui.graphics.Color(0xFF2B2724)))))
            Box(Modifier.fillMaxSize().pointerInput(viewAspect) {
                detectTapGestures(
                    onTap = { o ->
                        val u = o.x / size.width; val v = o.y / size.height
                        focusPoint = u to v
                        vm.focusAt(u, v, viewAspect)
                    },
                    onDoubleTap = { focusPoint = null; vm.clearTouchFocus() },
                )
            })
            val disp = s.disp
            if (s.grid || disp == DispMode.COMPOSE) GridOverlay(Modifier.fillMaxSize())
            if (s.level || disp == DispMode.COMPOSE) LevelOverlay(levelDegrees, Modifier.fillMaxSize())
            focusPoint?.let { (x, y) ->
                val live by vm.controller!!.live.collectAsState()
                FocusBox(x, y, live.afState == android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED, Modifier.fillMaxSize())
            }
            if ((s.histogram || disp == DispMode.PRO) && stats != null) {
                Histogram(stats!!.histY, stats!!.histR, stats!!.histG, stats!!.histB, Modifier.align(Alignment.TopEnd).padding(8.dp).size(112.dp, 58.dp))
            }
            if (disp != DispMode.CLEAN) InfoStrip(vm, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            when (val st = state) {
                is CamState.Error -> Box(Modifier.fillMaxSize().background(Hc.Black).tap { vm.reconfigure() }, contentAlignment = Alignment.Center) {
                    Text(st.message + if (st.recoverable) "\n\nTap to retry." else "", style = Hc.body, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
                CamState.Opening, CamState.Idle -> {}
                is CamState.Ready -> {}
            }
        }
    }
}

@Composable
private fun InfoStrip(vm: CameraViewModel, modifier: Modifier) {
    val s by vm.settings.collectAsState()
    val ev = rememberExposureView(vm)
    val p = s.current
    val stats by vm.stats.collectAsState()
    val plan = vm.plan()
    val approx = (vm.controller?.tonemapHonored == false) || (p.mode == ShootingMode.M && p.shutterS > 0.066)
    val parts = mutableListOf(ev.shutter, ev.aperture, if (ev.isoAuto) (ev.isoSub ?: "ISO AUTO") else "ISO ${ev.iso}", Stops.formatEv(p.exposureComp))
    if (s.disp == DispMode.PRO) {
        parts += p.focusMode.label
        parts += vm.fixedKelvin()?.let { "${it.first}K" } ?: p.wbMode.label
        plan?.let { parts += "${"%.1f".format(java.util.Locale.US, it.effectiveMegapixels)}MP" }
        val fps = stats?.fps
        if (fps != null && fps > 0) parts += "${fps.toInt()}fps"
    }
    if (vm.thermalThrottled()) parts += "HOT"
    Row(
        modifier.background(androidx.compose.ui.graphics.Color(0x88000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(parts.joinToString("   "), style = Hc.small, maxLines = 1)
        if (approx) Text("≈", style = Hc.small.copy(color = Hc.Amber))
    }
}

@Composable
private fun GlViewfinder(vm: CameraViewModel, modifier: Modifier) {
    val lut by vm.previewLut.collectAsState()
    val s by vm.settings.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<GLSurfaceView>(1) }
    val renderer = remember {
        PreviewRenderer(
            onSurfaceTexture = { st -> holder[0]?.post { vm.onPreviewSurfaceReady(st) } },
            requestRender = { holder[0]?.requestRender() },
            onStats = { vm.onStats(it) },
            onError = { msg -> holder[0]?.post { vm.onGpuError(msg) } },
        )
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(lut) { lut?.let { renderer.setLut(it) } }
    LaunchedEffect(Unit) {
        while (true) {
            vm.controller?.previewBufferSize?.let { renderer.bufferWidth = it.width; renderer.bufferHeight = it.height }
            renderer.displayRotation = vm.displayRotation
            renderer.magnification = if (vm.magnifying) 3f else 1f
            vm.thumbnailRequest?.let { cb -> vm.thumbnailRequest = null; renderer.requestThumbnail(cb) }
            delay(100)
        }
    }
    renderer.zebras = s.zebras
    renderer.zebraLevel = s.zebraLevel / 100f
    renderer.peaking = s.peaking && s.current.focusMode == FocusMode.MF
    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                holder[0] = this
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LookChip(name: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(48.dp).clip(RoundedCornerShape(4.dp)).background(Hc.Panel).border(1.dp, Hc.Line, RoundedCornerShape(4.dp))
            .tap(onClick = onClick).padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LOOK", style = Hc.label.copy(fontSize = 8.sp))
        Text(name.removeSuffix(" INSPIRED"), style = Hc.small.copy(fontSize = 11.sp), maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}
