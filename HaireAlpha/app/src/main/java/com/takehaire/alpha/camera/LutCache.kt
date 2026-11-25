package com.takehaire.alpha.camera

import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.look.LookSpec
import com.takehaire.alpha.imaging.lut.Lut3D
import com.takehaire.alpha.imaging.lut.LutBaker

/**
 * Bakes and caches look LUTs. Scene LUTs (65³) are keyed by look content, intensity, white point
 * (quantised to 0.25 EV) and input type; a small LRU keeps the last few so repeated shots with the
 * same look cost nothing.
 */
class LutCache {
    private val scene = object : LinkedHashMap<String, Lut3D>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lut3D>?) = size > 4
    }

    @Synchronized
    fun scene(look: LookSpec, intensity: Double, whiteEv: Double, displayReferred: Boolean, post: Lut3D?): Lut3D {
        val wq = (whiteEv * 4).let { kotlin.math.round(it) } / 4.0
        val key = "${look.hashCode()}|${"%.2f".format(java.util.Locale.ROOT, intensity)}|$wq|$displayReferred|${post?.hashCode()}"
        return scene.getOrPut(key) {
            LutBaker.bakeScene(look, Assets.alphaNatural, intensity, wq, size = 65, post = post, displayReferredInput = displayReferred)
        }
    }

    /** Preview LUT for the GPU, input = camera preview (sRGB preset curve when honoured). */
    fun preview(look: LookSpec, intensity: Double, vendorCurveIsSrgb: Boolean, post: Lut3D?): Lut3D =
        LutBaker.bakePreview(look, Assets.alphaNatural, intensity, LutBaker.SourceModel(1.0, vendorCurveIsSrgb), size = 33, post = post)

    /** Small LUT for live look thumbnails (CPU). */
    fun thumb(look: LookSpec, vendorCurveIsSrgb: Boolean): Lut3D =
        LutBaker.bakePreview(look, Assets.alphaNatural, 1.0, LutBaker.SourceModel(1.0, vendorCurveIsSrgb), size = 17)
}
