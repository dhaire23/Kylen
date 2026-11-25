package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.lens.LensMath
import com.takehaire.alpha.imaging.process.Bokeh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.sqrt

class BokehTest {
    private val w = 600
    private val h = 400

    /** Red textured subject disc on a green/blue checker background with a few bright lights. */
    private fun scene(): Pair<IntArray, FloatArray> {
        val img = IntArray(w * h)
        val mask = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val inSubject = hypot(x - 300.0, y - 220.0) < 110
            val c = if (inSubject) {
                val t = if ((x / 3 + y / 3) % 2 == 0) 200 else 150
                (0xFF shl 24) or (t shl 16) or (40 shl 8) or 30
            } else {
                val chk = ((x / 12) + (y / 12)) % 2 == 0
                val light = (x % 97 == 20 && y % 83 == 30)
                if (light) 0xFFFFFFFF.toInt() else if (chk) 0xFF2A8C3C.toInt() else 0xFF2A4F9C.toInt()
            }
            img[y * w + x] = c
            mask[y * w + x] = if (inSubject) 1f else 0f
        }
        return img to mask
    }

    private fun std(img: IntArray, xs: IntRange, ys: IntRange): Double {
        val v = ArrayList<Double>()
        for (y in ys) for (x in xs) v.add(((img[y * w + x] shr 8) and 0xFF).toDouble())
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    @Test fun backgroundBlursSubjectUntouchedNoHalo() {
        val (img, mask) = scene()
        val orig = img.copyOf()
        val frac = LensMath.blurFraction(85.0, 1.4, 1.5, 6.0)
        val r = Bokeh.apply(img, w, h, mask, w, h, Bokeh.Params(frac, workLongSide = 600, threads = 2))
        assertTrue(r.reason, r.applied)
        // Subject interior identical.
        for (y in 180..260) for (x in 260..340) assertEquals(orig[y * w + x], img[y * w + x])
        // Background texture strongly reduced.
        assertTrue(std(img, 20..120, 20..120) < 0.5 * std(orig, 20..120, 20..120))
        // No red subject colour bleeding into the background just outside the subject edge.
        for (a in 0 until 360 step 15) {
            val rad = Math.toRadians(a.toDouble())
            val x = (300 + 122 * kotlin.math.cos(rad)).toInt(); val y = (220 + 122 * kotlin.math.sin(rad)).toInt()
            if (y !in 0 until h) continue
            val red = (img[y * w + x] shr 16) and 0xFF
            assertTrue("halo at $a° red=$red", red < 90)
        }
        val out = BufferedImage(w * 2, h, BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, w, h, orig, 0, w); out.setRGB(w, 0, w, h, img, 0, w)
        System.getProperty("haire.validationOut")?.let { File(it).mkdirs(); ImageIO.write(out, "jpg", File(it, "bokeh_synthetic.jpg")) }
    }

    @Test fun deeperBackgroundGetsMoreBlurThanAreaNextToSubject() {
        // Point lights on a dark background: the size of each light's bokeh disc is the blur radius there.
        val (img, mask) = scene()
        for (i in img.indices) if (mask[i] == 0f) img[i] = 0xFF101010.toInt()
        val near = 245 to 350 // ~20 px below the subject
        val far = 530 to 70 // far from the subject
        for ((x, y) in listOf(near, far)) for (dy in -1..1) for (dx in -1..1) img[(y + dy) * w + x + dx] = 0xFFFFFFFF.toInt()
        Bokeh.apply(img, w, h, mask, w, h, Bokeh.Params(LensMath.blurFraction(85.0, 1.4, 1.5, 6.0), workLongSide = 600, threads = 2))
        fun footprint(c: Pair<Int, Int>): Int {
            var n = 0
            for (y in c.second - 40..c.second + 40) for (x in c.first - 40..c.first + 40) {
                if (x !in 0 until w || y !in 0 until h || mask[y * w + x] > 0f) continue
                if ((img[y * w + x] shr 8 and 0xFF) > 0x14) n++
            }
            return n
        }
        val fn = footprint(near)
        val ff = footprint(far)
        assertTrue("near footprint $fn far footprint $ff", ff > fn * 1.5)
    }

    @Test fun uncertainOrImplausibleMasksAreSkipped() {
        val (img, _) = scene()
        val orig = img.copyOf()
        val p = Bokeh.Params(0.03, workLongSide = 600, threads = 2)
        assertFalse(Bokeh.apply(img, w, h, FloatArray(w * h) { 0.5f }, w, h, p).applied)
        assertFalse(Bokeh.apply(img, w, h, FloatArray(w * h), w, h, p).applied)
        assertFalse(Bokeh.apply(img, w, h, FloatArray(w * h) { 1f }, w, h, p).applied)
        assertTrue(img.contentEquals(orig))
    }

    @Test fun smallApertureOrWideLensGivesLittleBlur() {
        val wide = LensMath.blurFraction(24.0, 8.0, 3.0, 12.0)
        val tele = LensMath.blurFraction(85.0, 1.4, 1.5, 6.0)
        assertTrue(tele > 20 * wide)
        assertTrue("tele $tele", tele in 0.015..0.08)
    }
}
