package com.takehaire.alpha.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.calib.ProcessingModes
import com.takehaire.alpha.imaging.calib.SensorProfile
import com.takehaire.alpha.imaging.color.Vec3
import com.takehaire.alpha.imaging.exposure.ShootingMode
import com.takehaire.alpha.imaging.lens.AspectRatio
import com.takehaire.alpha.imaging.lens.LensMath
import com.takehaire.alpha.imaging.look.LookSpec
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.imaging.process.DevelopParams
import com.takehaire.alpha.imaging.process.StillDeveloper
import com.takehaire.alpha.imaging.process.StripeSink
import com.takehaire.alpha.imaging.raw.Cfa
import com.takehaire.alpha.imaging.raw.IntRect
import com.takehaire.alpha.imaging.raw.NoiseProfile
import com.takehaire.alpha.imaging.raw.RawFrame
import com.takehaire.alpha.imaging.raw.RawSceneSource
import com.takehaire.alpha.imaging.raw.RgbSceneSource
import com.takehaire.alpha.imaging.raw.SceneSource
import com.takehaire.alpha.imaging.raw.ShadingMap
import com.takehaire.alpha.settings.ImageFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Everything about the photographer's intent needed to develop one capture. */
data class CaptureContext(
    val look: LookSpec,
    val intensity: Double,
    val processing: ProcessingMode,
    val format: ImageFormat,
    val aspect: AspectRatio,
    /** Digital magnification still to apply on RAW (RAW is never cropped by the HAL). */
    val rawDigitalCrop: Double,
    val rotation: Int,
    val mode: ShootingMode,
    val exposureComp: Double,
    val manualWb: Pair<Int, Int>?,
    val wbShift: Pair<Double, Double>,
    val profile: SensorProfile,
    val learnedBaselineEv: Double?,
    /** Centre-weighted linear luminance of the preview at the moment of capture (pre-look). */
    val previewMeter: Float?,
    val previewProduct: Double,
    val tonemapHonored: Boolean,
    val lensName: String,
    val equivalentFocal: Double,
    val virtualAperture: Double?,
    val cubeLut: Lut3D?,
)

data class ProcessedPhoto(
    val uris: List<Uri>,
    val thumbnail: Bitmap?,
    val millis: Long,
    val pathwayUsed: String,
    val exposureNs: Long,
    val iso: Int,
    val anchorEv: Double?,
    val whiteEv: Double,
    val physicalId: String?,
    val note: String?,
)

/**
 * Turns a [CaptureBundle] into finished files. RAW frames are developed by the HAIRE pipeline; YUV and
 * vendor-JPEG frames get the same look via the processed pathway. Failures fall back to the most
 * complete file available, and the photograph is never silently lost.
 */
class CaptureProcessor(
    private val context: Context,
    private val caps: DeviceCaps,
    private val store: PhotoStore,
    private val luts: LutCache,
) {
    fun process(b: CaptureBundle, c: CaptureContext): ProcessedPhoto {
        val t0 = System.currentTimeMillis()
        val timeMs = System.currentTimeMillis()
        val name = store.newBaseName(timeMs)
        val uris = ArrayList<Uri>()
        val result = b.physicalResult ?: b.result
        val physId = b.result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        val info = physId?.let { caps.info(it) }?.takeIf { it.colorModel != null } ?: caps.info(b.cameraId)!!
        val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
        var note: String? = null

        // 1) DNG first: the sensor data is the most valuable thing we have.
        if (b.rawData != null && c.format != ImageFormat.JPEG) {
            try {
                uris.add(saveDng(b, info.characteristics, result, "$name.dng", timeMs, c))
            } catch (e: Exception) {
                note = "DNG could not be written (${e.message}); JPEG was saved."
            }
        }

        var thumb: Bitmap? = null
        var anchorEv: Double? = null
        var whiteEv = 2.47
        var used = b.pathway.label
        if (c.format != ImageFormat.RAW || b.rawData == null) {
            try {
                val dev = develop(b, c, info, result, iso)
                anchorEv = dev.anchorEv
                whiteEv = dev.whiteEv
                val tmp = File(context.cacheDir, "$name.jpg")
                try {
                    tmp.outputStream().use { dev.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    writeExif(tmp, c, info, result, dev.bitmap.width, dev.bitmap.height, timeMs, b.pathway)
                    uris.add(store.saveFile("$name.jpg", "image/jpeg", timeMs, dev.bitmap.width, dev.bitmap.height, tmp))
                } finally {
                    tmp.delete()
                }
                thumb = Bitmap.createScaledBitmap(dev.bitmap, dev.bitmap.width / 8, dev.bitmap.height / 8, true)
                dev.bitmap.recycle()
            } catch (e: Throwable) {
                // Fallback: save the vendor JPEG captured alongside RAW, so the moment is not lost.
                val jpg = b.jpeg
                if (jpg != null) {
                    uris.add(store.save("$name.jpg", "image/jpeg", timeMs, 0, 0) { it.write(jpg) })
                    used = "Vendor JPEG (HAIRE development failed: ${e.javaClass.simpleName})"
                    note = "This photo used the phone's own processing because development failed."
                } else if (uris.isEmpty()) {
                    throw e
                }
            }
        } else {
            // RAW only: still render a review thumbnail.
            try {
                val dev = develop(b, c, info, result, iso, previewOnly = true)
                thumb = dev.bitmap
                whiteEv = dev.whiteEv
            } catch (_: Throwable) {}
        }
        return ProcessedPhoto(uris, thumb, System.currentTimeMillis() - t0, used, expNs, iso, anchorEv, whiteEv, physId, note)
    }

    private class Developed(val bitmap: Bitmap, val anchorEv: Double?, val whiteEv: Double)

    private fun develop(
        b: CaptureBundle, c: CaptureContext, info: CameraInfo, result: CaptureResult, iso: Int,
        previewOnly: Boolean = false,
    ): Developed {
        val prof = ProcessingModes.profile(c.processing)
        val look = c.look.copy(warmth = c.look.warmth + c.wbShift.first, tint = c.look.tint + c.wbShift.second)
        val source: SceneSource
        val crop: IntRect
        var anchorEv: Double? = null
        val whiteEv: Double
        var displayReferred = false
        val raw = b.rawData
        if (b.pathway == Pathway.RAW && raw != null && info.colorModel != null) {
            val frame = buildRawFrame(b, info, result, c, iso)
            val baseEv = c.learnedBaselineEv ?: c.profile.baselineExposureEv
            var k = 2.0.pow(baseEv)
            val r = rawCrop(b, result, c)
            crop = r
            // Anchor RAW brightness to what the camera's meter showed (auto exposure modes only).
            if (c.mode != ShootingMode.M && c.previewMeter != null && c.tonemapHonored && c.previewMeter > 1e-4f) {
                val probe = RawSceneSource(frame, 1f).proxy(r, (minOf(r.width, r.height) / 120).coerceAtLeast(2))
                val lraw = centreWeighted(probe)
                if (lraw > 1e-6) {
                    val evAnchor = ln(c.previewMeter / lraw) / ln(2.0)
                    val clamped = evAnchor.coerceIn(baseEv - c.profile.anchorRangeEv, baseEv + c.profile.anchorRangeEv)
                    anchorEv = clamped
                    k = 2.0.pow(clamped)
                }
            }
            source = RawSceneSource(frame, k.toFloat())
            whiteEv = (ln(k / 0.18) / ln(2.0)).coerceIn(1.5, 5.5)
        } else {
            val argb: IntArray
            val w: Int
            val h: Int
            if (b.yuvArgb != null) {
                argb = b.yuvArgb; w = b.yuvWidth; h = b.yuvHeight
            } else {
                val jpg = b.jpeg ?: error("No image data")
                val bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.size) ?: error("JPEG decode failed")
                w = bmp.width; h = bmp.height
                argb = IntArray(w * h); bmp.getPixels(argb, 0, w, 0, 0, w, h); bmp.recycle()
                // Vendor JPEG is already oriented by JPEG_ORIENTATION metadata? Camera2 JPEGs carry the
                // orientation in EXIF only, so pixels are sensor-oriented like YUV.
            }
            displayReferred = !(c.tonemapHonored && b.yuvArgb != null)
            source = RgbSceneSource(argb, w, h, 1f, iso)
            val cr = LensMath.cropRect(w, h, c.aspect, 1.0)
            crop = IntRect(cr[0], cr[1], cr[2], cr[3])
            whiteEv = ln(1.0 / 0.18) / ln(2.0)
        }
        val lut = luts.scene(look, c.intensity, whiteEv, displayReferred, c.cubeLut)
        val cropSharpen = 1.0 / (1.0 + c.profile.cropSharpenFalloff * (c.rawDigitalCrop - 1.0).coerceAtLeast(0.0))
        val scale = if (previewOnly) 8 else 1
        val outW = if (c.rotation % 180 == 0) crop.width else crop.height
        val outH = if (c.rotation % 180 == 0) crop.height else crop.width
        val params = DevelopParams(
            lut = lut,
            crop = crop,
            nrLuma = (c.profile.at(c.profile.nrLuma, iso) * prof.nrScale).toFloat().coerceIn(0f, 1f),
            nrChroma = (c.profile.at(c.profile.nrChroma, iso) * prof.nrScale).toFloat().coerceIn(0f, 1f),
            sharpenAmount = (prof.sharpenAmount * look.sharpening * c.profile.at(c.profile.sharpen, iso) * cropSharpen).toFloat(),
            grain = if (prof.spatialEffects) look.grain else null,
            halation = if (prof.spatialEffects) look.halation else null,
            vignetteStops = if (prof.spatialEffects) look.vignette.toFloat() else 0f,
            clarity = look.clarity.toFloat(),
            localTone = prof.localTone.toFloat(),
            seed = (b.timestampNs and 0x7FFFFFFF).toInt(),
        )
        if (previewOnly) {
            // Small review image for RAW-only captures: develop the proxy region only.
            val small = IntRect(crop.x, crop.y, crop.width, crop.height)
            val bmp = Bitmap.createBitmap(outW / scale, outH / scale, Bitmap.Config.ARGB_8888)
            val p = source.proxy(small, scale)
            val px = IntArray(p.width * p.height)
            val o = FloatArray(3)
            for (i in px.indices) {
                lut.lookup(
                    com.takehaire.alpha.imaging.lut.LutBaker.SceneLog.encode(p.rgb[i * 3]),
                    com.takehaire.alpha.imaging.lut.LutBaker.SceneLog.encode(p.rgb[i * 3 + 1]),
                    com.takehaire.alpha.imaging.lut.LutBaker.SceneLog.encode(p.rgb[i * 3 + 2]), o,
                )
                px[i] = (0xFF shl 24) or ((o[0] * 255).toInt().coerceIn(0, 255) shl 16) or ((o[1] * 255).toInt().coerceIn(0, 255) shl 8) or (o[2] * 255).toInt().coerceIn(0, 255)
            }
            val sinkBmp = Bitmap.createBitmap(px, p.width, p.height, Bitmap.Config.ARGB_8888)
            val m = android.graphics.Matrix().apply { postRotate(c.rotation.toFloat()) }
            bmp.recycle()
            return Developed(Bitmap.createBitmap(sinkBmp, 0, 0, sinkBmp.width, sinkBmp.height, m, true), anchorEv, whiteEv)
        }
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        StillDeveloper(source, params).develop(BitmapSink(bitmap, crop.width, crop.height, c.rotation))
        return Developed(bitmap, anchorEv, whiteEv)
    }

    private fun centreWeighted(p: com.takehaire.alpha.imaging.raw.ProxyImage): Double {
        var s = 0.0
        var ws = 0.0
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val dx = (x - p.width / 2.0) / p.width
            val dy = (y - p.height / 2.0) / p.height
            val w = 1.0 / (1.0 + 12.0 * (dx * dx + dy * dy))
            s += p.luminance(y * p.width + x) * w
            ws += w
        }
        return s / ws
    }

    private fun rawCrop(b: CaptureBundle, result: CaptureResult, c: CaptureContext): IntRect {
        var bx = 0; var by = 0; var bw = b.rawWidth; var bh = b.rawHeight
        if (Build.VERSION.SDK_INT >= 34) {
            result.get(CaptureResult.SCALER_RAW_CROP_REGION)?.let { r ->
                if (r.width() > 16 && r.height() > 16 && r.right <= b.rawWidth && r.bottom <= b.rawHeight) {
                    bx = r.left; by = r.top; bw = r.width(); bh = r.height()
                }
            }
        }
        val zoom = if (bw == b.rawWidth && bh == b.rawHeight) c.rawDigitalCrop else 1.0
        val cr = LensMath.cropRect(bw, bh, c.aspect, zoom.coerceAtLeast(1.0))
        return IntRect((bx + cr[0]) and 0x7FFFFFFE, (by + cr[1]) and 0x7FFFFFFE, cr[2], cr[3])
    }

    private fun buildRawFrame(b: CaptureBundle, info: CameraInfo, r: CaptureResult, c: CaptureContext, iso: Int): RawFrame {
        val ch = info.characteristics
        val black = FloatArray(4)
        val dyn = r.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
        if (dyn != null && dyn.size == 4) {
            for (i in 0..3) black[i] = dyn[i]
        } else {
            val pat = ch.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
            for (i in 0..3) black[i] = pat?.getOffsetForIndex(i % 2, i / 2)?.toFloat() ?: 64f
        }
        val white = (r.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) ?: info.whiteLevel ?: 1023).toFloat()
        val shading = r.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)?.let { m ->
            val g = FloatArray(m.rowCount * m.columnCount * 4)
            m.copyGainFactors(g, 0)
            ShadingMap(m.columnCount, m.rowCount, g)
        }
        val noise = r.get(CaptureResult.SENSOR_NOISE_PROFILE)?.let { np ->
            NoiseProfile(DoubleArray(np.size) { np[it].first }, DoubleArray(np.size) { np[it].second })
        } ?: NoiseProfile.estimate(iso)
        val model = info.colorModel!!
        val neutral = c.manualWb?.let { (k, t) -> model.neutralForWhiteBalance(k.toDouble(), t.toDouble()) }
            ?: r.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.let { n -> Vec3(n[0].toDouble(), n[1].toDouble(), n[2].toDouble()) }
            ?: model.neutralForWhiteBalance(5500.0, 0.0)
        return RawFrame(
            data = b.rawData!!, width = b.rawWidth, height = b.rawHeight, rowStride = b.rawRowStride,
            cfa = Cfa.fromCamera2(info.cfa ?: 0) ?: Cfa.RGGB, blackLevel = black, whiteLevel = white,
            shading = shading, noise = noise, neutral = neutral / neutral.maxComponent(), colorModel = model,
            iso = iso, exposureTimeNs = r.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
        )
    }

    private fun saveDng(b: CaptureBundle, ch: CameraCharacteristics, r: CaptureResult, name: String, timeMs: Long, c: CaptureContext): Uri {
        val rr = r as? android.hardware.camera2.TotalCaptureResult ?: b.result
        DngCreator(ch, rr).use { dng ->
            dng.setOrientation(exifOrientation(c.rotation))
            dng.setDescription("HAIRE Alpha · look ${c.look.name} (not applied to RAW data)")
            val packed = ByteBuffer.allocateDirect(b.rawWidth * b.rawHeight * 2).order(ByteOrder.nativeOrder())
            val sb = packed.asShortBuffer()
            for (y in 0 until b.rawHeight) sb.put(b.rawData!!, y * b.rawRowStride, b.rawWidth)
            packed.position(0)
            return store.save(name, "image/x-adobe-dng", timeMs, b.rawWidth, b.rawHeight) { out ->
                dng.writeByteBuffer(out, Size(b.rawWidth, b.rawHeight), packed, 0)
            }
        }
    }

    private fun exifOrientation(rot: Int) = when (rot) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun writeExif(file: File, c: CaptureContext, info: CameraInfo, r: CaptureResult, w: Int, h: Int, timeMs: Long, pathway: Pathway) {
        val e = ExifInterface(file)
        val fmt = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val now = fmt.format(java.util.Date(timeMs))
        val tz = java.text.SimpleDateFormat("XXX", Locale.US).format(java.util.Date(timeMs))
        e.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        e.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        e.setAttribute(ExifInterface.TAG_SOFTWARE, "HAIRE Alpha ${com.takehaire.alpha.BuildConfig.VERSION_NAME}")
        e.setAttribute(ExifInterface.TAG_DATETIME, now)
        e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, now)
        e.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, now)
        e.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, tz)
        e.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        e.setAttribute(ExifInterface.TAG_COLOR_SPACE, ExifInterface.COLOR_SPACE_S_RGB.toString())
        e.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, w.toString())
        e.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, h.toString())
        // Actual physical capture values, read back from the capture result (never the virtual lens).
        r.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { e.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (it / 1e9).toString()) }
        r.get(CaptureResult.SENSOR_SENSITIVITY)?.let { e.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
        (r.get(CaptureResult.LENS_APERTURE) ?: info.apertures.firstOrNull())?.let { e.setAttribute(ExifInterface.TAG_F_NUMBER, "%.2f".format(Locale.US, it)) }
        (r.get(CaptureResult.LENS_FOCAL_LENGTH) ?: info.focalLengths.firstOrNull())?.let {
            e.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(it * 1000).roundToInt()}/1000")
        }
        e.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, c.equivalentFocal.roundToInt().toString())
        e.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, when (c.mode) {
            ShootingMode.M -> "1"; ShootingMode.P, ShootingMode.AUTO -> "2"; ShootingMode.A -> "3"; ShootingMode.S -> "4"
        })
        e.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "${(c.exposureComp * 3).roundToInt()}/3")
        e.setAttribute(ExifInterface.TAG_WHITE_BALANCE, if (c.manualWb == null) "0" else "1")
        e.setAttribute(ExifInterface.TAG_FLASH, "0")
        e.setAttribute(ExifInterface.TAG_LENS_MAKE, Build.MANUFACTURER)
        e.setAttribute(ExifInterface.TAG_LENS_MODEL, "${Build.MODEL} camera ${info.id} ${info.focalLengths.firstOrNull() ?: "?"}mm f/${info.apertures.firstOrNull() ?: "?"}")
        val va = c.virtualAperture?.let { "F%.1f".format(Locale.US, it) } ?: "off"
        e.setAttribute(
            ExifInterface.TAG_USER_COMMENT,
            "HAIRE Alpha | Look: ${c.look.name} ${(c.intensity * 100).roundToInt()}% | Virtual lens: ${c.lensName} " +
                "(digital crop ${"%.2f".format(Locale.US, c.rawDigitalCrop)}x) | Virtual aperture: $va | " +
                "Processing: ${c.processing.label} | Pathway: ${pathway.name}",
        )
        e.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "${c.look.name} · ${c.lensName}")
        e.saveAttributes()
    }
}

/** Writes rotated stripes straight into the output bitmap (no second full-size buffer). */
class BitmapSink(private val bmp: Bitmap, private val srcW: Int, private val srcH: Int, rotation: Int) : StripeSink {
    private val rot = ((rotation % 360) + 360) % 360

    override fun write(cropRowStart: Int, width: Int, rows: Int, argb: IntArray) {
        val y1 = cropRowStart + rows - 1
        when (rot) {
            90 -> { // (x, y) → (H-1-y, x): block of `rows` columns × `width` rows
                val bw = rows
                val tmp = IntArray(bw * width)
                for (j in 0 until rows) for (i in 0 until width) tmp[i * bw + (rows - 1 - j)] = argb[j * width + i]
                synchronized(bmp) { bmp.setPixels(tmp, 0, bw, srcH - 1 - y1, 0, bw, width) }
            }
            180 -> { // (x, y) → (W-1-x, H-1-y)
                val tmp = IntArray(width * rows)
                for (j in 0 until rows) for (i in 0 until width) tmp[(rows - 1 - j) * width + (width - 1 - i)] = argb[j * width + i]
                synchronized(bmp) { bmp.setPixels(tmp, 0, width, srcW - width, srcH - 1 - y1, width, rows) }
            }
            270 -> { // (x, y) → (y, W-1-x)
                val bw = rows
                val tmp = IntArray(bw * width)
                for (j in 0 until rows) for (i in 0 until width) tmp[(width - 1 - i) * bw + j] = argb[j * width + i]
                synchronized(bmp) { bmp.setPixels(tmp, 0, bw, cropRowStart, srcW - width, bw, width) }
            }
            else -> synchronized(bmp) { bmp.setPixels(argb, 0, width, 0, cropRowStart, width, rows) }
        }
    }
}
