package com.takehaire.alpha.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.util.concurrent.TimeUnit

/** A subject confidence mask (0..1) and which model produced it. */
class SubjectMask(val values: FloatArray, val width: Int, val height: Int, val source: String)

/**
 * On-device subject segmentation for virtual bokeh. Prefers ML Kit Subject Segmentation (people,
 * pets, objects; a one-time model download through Google Play services) and falls back to the
 * bundled, fully offline Selfie Segmentation (people only). Never called on the main thread.
 */
class Segmenter(private val context: Context) {
    private val subject by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
    }
    private val selfie by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
    }

    /** Asks Play services to fetch the subject model ahead of time (no-op if already present). */
    fun prefetch() {
        try {
            ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(subject).build())
        } catch (_: Exception) {
        }
    }

    fun mask(bitmap: Bitmap): SubjectMask? {
        val input = InputImage.fromBitmap(bitmap, 0)
        try {
            val r = Tasks.await(subject.process(input), 8, TimeUnit.SECONDS)
            val buf = r.foregroundConfidenceMask
            if (buf != null) {
                buf.rewind()
                val a = FloatArray(buf.remaining()).also { buf.get(it) }
                if (a.size == bitmap.width * bitmap.height) return SubjectMask(a, bitmap.width, bitmap.height, "subject")
            }
        } catch (_: Exception) {
            // Model not downloaded yet, no Play services, or no subject: try the offline people model.
            prefetch()
        }
        return try {
            val m = Tasks.await(selfie.process(input), 8, TimeUnit.SECONDS)
            val bb = m.buffer
            bb.rewind()
            val fb = bb.asFloatBuffer()
            val a = FloatArray(m.width * m.height).also { fb.get(it) }
            SubjectMask(a, m.width, m.height, "people")
        } catch (_: Exception) {
            null
        }
    }
}
