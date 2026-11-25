package com.takehaire.alpha.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takehaire.alpha.CameraViewModel
import com.takehaire.alpha.camera.CamState
import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.exposure.Stops
import kotlinx.coroutines.delay
import java.util.Locale

/** Builds the diagnostics report (also used for export). Contains no personal data. */
fun diagnosticsReport(vm: CameraViewModel): String = buildString {
    val caps = vm.caps.value
    val ctl = vm.controller
    val live = vm.liveResult()
    val last = vm.last.value
    val st = ctl?.state?.value
    fun l(k: String, v: Any?) { append(k.padEnd(28)).append(v ?: "—").append('\n') }
    append("HAIRE Alpha diagnostics ${com.takehaire.alpha.BuildConfig.VERSION_NAME}\n\n")
    l("Device model", "${caps?.manufacturer} ${caps?.model}")
    l("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    l("Camera state", st)
    l("Active camera id", ctl?.currentSpec?.cameraId)
    l("Capture pathway", (st as? CamState.Ready)?.pathway?.label)
    l("Active physical camera", live.activePhysicalId ?: "(not reported)")
    l("sRGB tone curve honoured", ctl?.tonemapHonored)
    l("Preview buffer", ctl?.previewBufferSize)
    l("Preview FPS", vm.stats.value?.fps?.let { "%.1f".format(Locale.US, it) })
    l("Preview exposure", if (live.exposureNs > 0) Stops.formatShutter(live.exposureNs / 1e9) else null)
    l("Preview ISO (boost)", "${live.iso} (${live.boost}%)")
    l("Zoom ratio (result)", live.zoomRatio)
    l("Crop region", live.cropRegion)
    l("Focus distance (dpt)", live.focusDistance)
    l("AF state", live.afState)
    val d = vm.decision()
    l("Planned exposure", d?.let { "${Stops.formatShutter(it.exposureS)}  ISO ${it.iso}  meter ${Stops.formatEv(it.meterEv)}  ${it.warning}" })
    l("Virtual lens", vm.lens().name)
    vm.plan()?.let { p -> l("Lens plan", "camera ${p.source.cameraId} zoom ${"%.2f".format(Locale.US, p.zoomRatio)} crop ${"%.2f".format(Locale.US, p.digitalCrop)} ${p.cropClass} ${"%.1f".format(Locale.US, p.effectiveMegapixels)}MP") }
    l("Virtual aperture", Stops.formatAperture(vm.current.virtualAperture) + " (bokeh not available)")
    l("Active look", "${vm.activeLook.name} v${vm.activeLook.version}")
    l("Look library", "v${Assets.looks.version}")
    l("Calibration", Assets.calibration.profiles.joinToString { "${it.id} v${it.version} ${it.source}" })
    l("Learned RAW baseline (EV)", vm.settings.value.learnedBaseline.entries.joinToString { "${it.key}: ${"%.2f".format(Locale.US, it.value)}" }.ifEmpty { null })
    append('\n')
    if (last != null) {
        append("Last capture\n")
        l("  requested", "${Stops.formatShutter(last.requestedExposureNs / 1e9)} ISO ${last.requestedIso}")
        last.processed?.let { p ->
            l("  actual (capture result)", "${Stops.formatShutter(p.exposureNs / 1e9)} ISO ${p.iso}")
            l("  pathway", p.pathwayUsed)
            l("  processing time", "${p.millis} ms")
            l("  RAW anchor / white", "${p.anchorEv?.let { "%.2f EV".format(Locale.US, it) } ?: "—"} / +${"%.2f".format(Locale.US, p.whiteEv)} EV")
            l("  physical camera", p.physicalId)
        }
        append('\n')
    }
    append("Cameras\n")
    caps?.cameras?.forEach { c ->
        append("  id ${c.id} ${if (c.isBack) "back" else "front"}${if (c.isLogical) " logical ${c.physicalIds}" else ""}\n")
        append("    focal ${c.focalLengths} f/${c.apertures} sensor ${c.sensorSize} array ${c.activeArray}\n")
        append("    eq 3:2 ${c.equivalentFocal(com.takehaire.alpha.imaging.lens.AspectRatio.R3_2)?.let { "%.1fmm".format(Locale.US, it) }}  ISO ${c.isoRange}  exp ${c.exposureRangeNs?.let { "${it.first}..${it.last}ns" }}\n")
        append("    hwLevel ${c.hwLevel} manualSensor ${c.manualSensor} manualPost ${c.manualPostProcessing} RAW ${c.raw} developable ${c.canDevelopRaw}\n")
        append("    RAW ${c.maxRaw} YUV ${c.maxYuv} JPEG ${c.maxJpeg} depth ${c.depthOutput} OIS ${c.oisModes} zoom ${c.zoomRatioRange}\n")
        append("    tonemap ${c.tonemapModes} edge ${c.edgeModes} NR ${c.nrModes} shadingMap ${c.shadingMapModes} colorMatrices ${c.colorModel != null}\n")
    }
    caps?.physical?.values?.forEach { c ->
        append("  physical ${c.id}: focal ${c.focalLengths} f/${c.apertures} RAW ${c.raw} eq ${c.equivalentFocal(com.takehaire.alpha.imaging.lens.AspectRatio.R3_2)?.let { "%.1f".format(Locale.US, it) }}\n")
    }
    append("\nLimitations\n")
    append("  - Virtual bokeh (computational depth) is not available in this build.\n")
    append("  - Preview is an approximation when the sRGB tone curve is not honoured (≈ shown).\n")
    append("  - Samsung's own multi-frame processing is not available to third-party Camera2 apps.\n")
}

@Composable
fun DiagnosticsScreen(vm: CameraViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick.intValue++ } }
    @Suppress("UNUSED_VARIABLE") val t = tick.intValue
    val stats by vm.stats.collectAsState()
    @Suppress("UNUSED_EXPRESSION") stats
    val report = diagnosticsReport(vm)
    Column(Modifier.fillMaxSize().background(Hc.Charcoal).safeDrawingPadding().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DIAGNOSTICS", style = Hc.value, modifier = Modifier.weight(1f))
            Chip("Export") {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Export diagnostics"))
            }
            Spacer(Modifier.width(6.dp))
            Chip("Close", onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(report, style = Hc.small.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
        }
    }
}
