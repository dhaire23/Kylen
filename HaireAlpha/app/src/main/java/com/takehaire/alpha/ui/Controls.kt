package com.takehaire.alpha.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Hc {
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF1B1B1D)
    val Panel = Color(0xFF242426)
    val Line = Color(0xFF3A3A3C)
    val WarmWhite = Color(0xFFEDE6DA)
    val Dim = Color(0xFF8C877F)
    val Amber = Color(0xFFE8892B)
    val Warn = Color(0xFFE0563B)
    val Mono = FontFamily.SansSerif

    val value = TextStyle(color = WarmWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", letterSpacing = 0.2.sp)
    val label = TextStyle(color = Dim, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
    val small = TextStyle(color = WarmWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val body = TextStyle(color = WarmWhite, fontSize = 15.sp)
}

/** Click with a subtle haptic tick. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val view = LocalView.current
    return this.clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClick()
    }
}

/**
 * Tactile exposure dial: drag horizontally to step in photographic stops (one haptic click per
 * stop), tap to open the precise value wheel. Shows "auto" values dimmed and warnings in red.
 */
@Composable
fun ExposureDial(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    auto: Boolean = false,
    warning: Boolean = false,
    locked: Boolean = false,
    reversed: Boolean = false,
    subLabel: String? = null,
    onStep: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val view = LocalView.current
    val acc = remember { mutableFloatStateOf(0f) }
    val step by rememberUpdatedState(onStep)
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
    Column(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Hc.Panel)
            .border(1.dp, if (active && !auto) Hc.Line else Hc.Panel, RoundedCornerShape(4.dp))
            .pointerInput(locked, reversed) {
                if (locked) return@pointerInput
                detectHorizontalDragGestures(onDragEnd = { acc.floatValue = 0f }) { _, dx ->
                    acc.floatValue += dx
                    while (abs(acc.floatValue) >= stepPx) {
                        val dir = if (acc.floatValue > 0) 1 else -1
                        acc.floatValue -= dir * stepPx
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        step(if (reversed) -dir else dir)
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .semantics { contentDescription = "$label $value" }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Hc.label, maxLines = 1, softWrap = false)
            if (locked) Text("  ⌧", style = Hc.label.copy(color = Hc.Amber))
        }
        Text(
            value,
            style = Hc.value.copy(color = when {
                warning -> Hc.Warn
                auto -> Hc.Dim
                active -> Hc.WarmWhite
                else -> Hc.Dim
            }),
            maxLines = 1,
            softWrap = false,
        )
        DialTicks(Modifier.fillMaxWidth().height(8.dp), acc.floatValue / stepPx)
        if (subLabel != null) Text(subLabel, style = Hc.label.copy(fontSize = 8.sp, letterSpacing = 0.4.sp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DialTicks(modifier: Modifier, phase: Float) {
    Canvas(modifier) {
        val n = 15
        val gap = size.width / n
        for (i in 0..n) {
            val x = (i + phase).mod(n.toFloat()) * gap
            val major = i % 3 == 0
            drawLine(if (abs(x - size.width / 2) < gap / 2) Hc.Amber else Hc.Line, Offset(x, if (major) 0f else size.height * 0.4f), Offset(x, size.height), strokeWidth = 1.5f)
        }
    }
}

@Composable
fun ShutterButton(busy: Int, enabled: Boolean, onPress: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Box(
        modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(3.dp, if (enabled) Hc.WarmWhite else Hc.Dim, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (busy > 0) Hc.Dim else if (enabled) Hc.WarmWhite else Hc.Line)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPress()
            }
            .semantics { contentDescription = "Shutter" },
        contentAlignment = Alignment.Center,
    ) {
        if (busy > 0) Text("$busy", style = Hc.small.copy(color = Hc.Black))
    }
}

/** Sony-style −3…+3 exposure scale. Drag to set exposure comp (or shows the meter in M). */
@Composable
fun ExposureMeter(
    ev: Double,
    meterMode: Boolean,
    warning: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onStep: (Int) -> Unit,
) {
    val view = LocalView.current
    val acc = remember { mutableFloatStateOf(0f) }
    val step by rememberUpdatedState(onStep)
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    Column(modifier.pointerInput(meterMode, locked) {
        if (meterMode || locked) return@pointerInput
        detectHorizontalDragGestures(onDragEnd = { acc.floatValue = 0f }) { _, dx ->
            acc.floatValue += dx
            while (abs(acc.floatValue) >= px) {
                val d = if (acc.floatValue > 0) 1 else -1
                acc.floatValue -= d * px
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                step(d)
            }
        }
    }) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val w = size.width
            val mid = w / 2
            val span = w * 0.42f
            for (i in -9..9) {
                val x = mid + i / 9f * span
                val major = i % 3 == 0
                drawLine(Hc.Dim, Offset(x, size.height * (if (major) 0.25f else 0.45f)), Offset(x, size.height * 0.75f), 1.2f)
            }
            val v = ev.coerceIn(-3.2, 3.2).toFloat()
            val x = mid + v / 3f * span
            val col = if (warning) Hc.Warn else Hc.Amber
            val p = Path().apply { moveTo(x, size.height * 0.95f); lineTo(x - 7f, size.height); lineTo(x + 7f, size.height); close() }
            drawLine(col, Offset(mid, size.height * 0.8f), Offset(x, size.height * 0.8f), 4f, cap = StrokeCap.Round)
            drawPath(p, col)
            if (abs(ev) > 3.2) drawCircle(col, 4f, Offset(if (ev > 0) w - 6f else 6f, size.height * 0.5f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−3", style = Hc.label); Text(if (meterMode) "M.M." else "EV " + com.takehaire.alpha.imaging.exposure.Stops.formatEv(ev), style = Hc.label.copy(color = if (warning) Hc.Warn else Hc.WarmWhite)); Text("+3", style = Hc.label)
        }
    }
}

/** Zoom ring constrained to the mounted zoom lens's range. */
@Composable
fun ZoomRing(min: Double, max: Double, value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val change by rememberUpdatedState(onChange)
    val cur by rememberUpdatedState(value)
    val marks = listOf(16, 20, 24, 28, 35, 40, 50, 70, 85, 100, 135, 200).filter { it >= min - 0.5 && it <= max + 0.5 }
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Hc.Panel)
            .pointerInput(min, max) {
                detectHorizontalDragGestures { _, dx ->
                    val f = cur * kotlin.math.exp(dx / size.width * kotlin.math.ln(max / min) * 1.2)
                    val nf = f.coerceIn(min, max)
                    if (marks.any { m -> (cur - m) * (nf - m) < 0 }) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    change(nf)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val lnMin = kotlin.math.ln(min)
            val lnMax = kotlin.math.ln(max)
            for (m in marks) {
                val x = ((kotlin.math.ln(m.toDouble()) - lnMin) / (lnMax - lnMin)).toFloat() * (size.width - 24f) + 12f
                drawLine(Hc.Dim, Offset(x, 4f), Offset(x, 12f), 1.5f)
            }
            val x = ((kotlin.math.ln(value) - lnMin) / (lnMax - lnMin)).toFloat() * (size.width - 24f) + 12f
            drawLine(Hc.Amber, Offset(x, 0f), Offset(x, size.height), 3f)
        }
        Text("${value.roundToInt()}mm", style = Hc.small)
    }
}

@Composable
fun Histogram(y: IntArray?, r: IntArray?, g: IntArray?, b: IntArray?, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0x99000000), RoundedCornerShape(3.dp)).padding(3.dp)) {
        fun draw(h: IntArray?, c: Color) {
            h ?: return
            val mx = (h.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val p = Path()
            p.moveTo(0f, size.height)
            for (i in h.indices) p.lineTo(i / (h.size - 1f) * size.width, size.height - h[i] / mx * size.height)
            p.lineTo(size.width, size.height); p.close()
            drawPath(p, c)
        }
        draw(r, Color(0x66E0563B)); draw(g, Color(0x6655B060)); draw(b, Color(0x664A7BD0)); draw(y, Color(0x99EDE6DA))
    }
}

@Composable
fun GridOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val c = Color(0x66EDE6DA)
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
        }
        drawLine(c, Offset(size.width / 2 - 10, size.height / 2), Offset(size.width / 2 + 10, size.height / 2), 1f)
        drawLine(c, Offset(size.width / 2, size.height / 2 - 10), Offset(size.width / 2, size.height / 2 + 10), 1f)
    }
}

@Composable
fun LevelOverlay(degrees: Float, modifier: Modifier) {
    Canvas(modifier) {
        val level = abs(degrees) < 0.8f
        val c = if (level) Color(0xFF6FCB7A) else Hc.WarmWhite
        val r = size.width * 0.16f
        val a = Math.toRadians(degrees.toDouble())
        val cx = size.width / 2; val cy = size.height / 2
        val dx = (cos(a) * r).toFloat(); val dy = (sin(a) * r).toFloat()
        drawLine(c, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), 2f)
        drawLine(Color(0x66EDE6DA), Offset(cx - r * 1.3f, cy), Offset(cx - r * 1.1f, cy), 2f)
        drawLine(Color(0x66EDE6DA), Offset(cx + r * 1.1f, cy), Offset(cx + r * 1.3f, cy), 2f)
    }
}

@Composable
fun FocusBox(x: Float, y: Float, confirmed: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val s = 34.dp.toPx()
        val c = if (confirmed) Color(0xFF6FCB7A) else Hc.WarmWhite
        drawRect(c, Offset(x * size.width - s / 2, y * size.height - s / 2), androidx.compose.ui.geometry.Size(s, s), style = Stroke(2f))
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .widthIn(min = 52.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Hc.Panel)
            .border(1.dp, if (highlighted) Hc.Amber else Hc.Line, RoundedCornerShape(4.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Hc.small.copy(color = if (highlighted) Hc.Amber else Hc.WarmWhite), maxLines = 1, textAlign = TextAlign.Center)
    }
}
