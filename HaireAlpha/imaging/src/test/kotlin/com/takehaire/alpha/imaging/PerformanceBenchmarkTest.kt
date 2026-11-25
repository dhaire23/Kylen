package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.lut.LutBaker
import com.takehaire.alpha.imaging.process.DevelopParams
import com.takehaire.alpha.imaging.process.StillDeveloper
import com.takehaire.alpha.imaging.process.StripeSink
import com.takehaire.alpha.imaging.raw.IntRect
import com.takehaire.alpha.imaging.raw.RawSceneSource
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 12 MP RAW development timing on this JVM. Run with -Pvalidate=true. */
class PerformanceBenchmarkTest {
    @Test fun develop12MpRaw() {
        assumeTrue(System.getProperty("haire.validate") == "true")
        val w = 4000; val h = 3000
        val scene = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val p = (y * w + x) * 3
            val v = (0.02 + 0.5 * ((x / 37 + y / 41) % 7) / 7.0).toFloat()
            scene[p] = v * 0.95f; scene[p + 1] = v; scene[p + 2] = v * 1.08f
        }
        val raw = SyntheticCamera().capture(scene, w, h, ColorMath.D65, 0.8f, iso = 800)
        val t0 = System.nanoTime()
        val lut = LutBaker.bakeScene(Assets.look("forever"), Assets.alphaNatural, 1.0, 3.0)
        val bake = (System.nanoTime() - t0) / 1e6
        val crop = IntRect(0, 166, 4000, 2666)
        var res: com.takehaire.alpha.imaging.process.DevelopResult? = null
        repeat(2) { // second run = warmed-up JIT
            res = StillDeveloper(RawSceneSource(raw, 1f), DevelopParams(lut, crop, grain = Assets.look("forever").grain, threads = Runtime.getRuntime().availableProcessors()))
                .develop(StripeSink { _, _, _, _ -> })
        }
        println("BENCH cores=${Runtime.getRuntime().availableProcessors()} lutBake65=${"%.0f".format(bake)}ms develop10.7MP=${res!!.millis}ms")
    }
}
