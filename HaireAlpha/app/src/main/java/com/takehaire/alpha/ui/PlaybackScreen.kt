package com.takehaire.alpha.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.takehaire.alpha.CameraViewModel
import com.takehaire.alpha.camera.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaybackScreen(vm: CameraViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember { mutableStateListOf<PhotoItem>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { vm.store.list() }
        items.clear(); items.addAll(list); loaded = true
    }
    Box(Modifier.fillMaxSize().background(Hc.Black).safeDrawingPadding()) {
        if (loaded && items.isEmpty()) {
            Text("No photographs yet.\nPress the shutter — they will appear here and in Samsung Gallery.", style = Hc.body, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        if (items.isNotEmpty()) {
            val pager = rememberPagerState { items.size }
            var info by remember { mutableStateOf(false) }
            var confirmDelete by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                ZoomableImage(items[page])
            }
            val item = items.getOrNull(pager.currentPage)
            if (info && item != null) InfoPanel(item, Modifier.align(Alignment.TopStart).padding(top = 56.dp))
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0x66000000)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("✕", onClick = onClose)
                Text("  ${pager.currentPage + 1} / ${items.size}", style = Hc.small, modifier = Modifier.weight(1f))
                Chip("INFO", highlighted = info) { info = !info }
            }
            if (item != null) Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0x66000000)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Chip(if (item.favorite) "★ Favourite" else "☆ Favourite", highlighted = item.favorite) {
                    if (vm.store.setFavorite(item.uri, !item.favorite)) items[pager.currentPage] = item.copy(favorite = !item.favorite)
                    else vm.notify("Favourites need Android 11 or newer.")
                }
                Chip("Share") {
                    val send = Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, item.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share photo"))
                }
                Chip("Delete") { confirmDelete = true }
            }
            if (confirmDelete && item != null) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Column(Modifier.background(Hc.Charcoal).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Delete this photo?", style = Hc.body)
                        Text(item.name, style = Hc.label.copy(letterSpacing = 0.sp))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip("Cancel") { confirmDelete = false }
                            Chip("Delete", highlighted = true) {
                                confirmDelete = false
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { try { vm.store.delete(item.uri) } catch (_: Exception) { false } }
                                    if (ok) items.remove(item) else vm.notify("This photo can only be deleted from Gallery.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(item: PhotoItem) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var full by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, item.uri, full) {
        value = withContext(Dispatchers.IO) { decode(ctx, item.uri, if (full) 8192 else 2400) }
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(item.uri) {
                detectTapGestures(onDoubleTap = {
                    // Double tap: 100 % pixel view (approx.) or back to fit.
                    if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else {
                        full = true
                        val fit = minOf(size.width.toFloat() / item.width.coerceAtLeast(1), size.height.toFloat() / item.height.coerceAtLeast(1))
                        scale = (1f / fit).coerceIn(2f, 12f)
                    }
                })
            }
            .pointerInput(item.uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 12f)
                    if (scale > 1.8f) full = true
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            Image(
                it.asImageBitmap(), item.name, contentScale = ContentScale.Fit, filterQuality = if (scale > 2f) FilterQuality.None else FilterQuality.Medium,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

private fun decode(ctx: android.content.Context, uri: Uri, maxSide: Int): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        val s = info.size
        val f = maxOf(s.width, s.height).toFloat() / maxSide
        if (f > 1f) d.setTargetSize((s.width / f).toInt(), (s.height / f).toInt())
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (_: Exception) {
    null
}

@Composable
private fun InfoPanel(item: PhotoItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val data by produceState<Pair<List<String>, IntArray?>>(emptyList<String>() to null, item.uri) {
        value = withContext(Dispatchers.IO) {
            val lines = ArrayList<String>()
            try {
                ctx.contentResolver.openInputStream(item.uri)?.use { s ->
                    val e = ExifInterface(s)
                    val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    if (t > 0) lines += "Shutter " + com.takehaire.alpha.imaging.exposure.Stops.formatShutter(t)
                    e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { lines += "ISO $it" }
                    e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { lines += "Physical aperture f/$it" }
                    e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { f ->
                        val mm = f.split('/').let { p -> if (p.size == 2) p[0].toDouble() / p[1].toDouble() else p[0].toDouble() }
                        lines += "Physical focal length %.2fmm".format(java.util.Locale.US, mm)
                    }
                    e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { lines += "Framing ≈ ${it}mm equivalent" }
                    e.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let { lines += "Exposure comp. $it EV" }
                    e.getAttribute(ExifInterface.TAG_USER_COMMENT)?.split(" | ")?.drop(1)?.let { lines.addAll(it) }
                }
            } catch (_: Exception) {}
            lines += "${item.width}×${item.height} · ${item.mime.substringAfter('/')}"
            val small = decode(ctx, item.uri, 256)
            val hist = small?.let { b ->
                val h = IntArray(64)
                val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                for (p in px) { val y = (54 * (p shr 16 and 0xFF) + 183 * (p shr 8 and 0xFF) + 19 * (p and 0xFF)) shr 8; h[y shr 2]++ }
                h
            }
            lines to hist
        }
    }
    Column(modifier.padding(10.dp).background(Color(0xAA000000)).padding(10.dp).width(260.dp)) {
        data.second?.let { Histogram(it, null, null, null, Modifier.size(240.dp, 70.dp)) }
        Spacer(Modifier.height(6.dp))
        for (l in data.first) Text(l, style = Hc.small.copy(fontSize = 11.sp))
    }
}
