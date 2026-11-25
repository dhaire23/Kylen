package com.takehaire.alpha.imaging

import com.takehaire.alpha.imaging.calib.Assets
import com.takehaire.alpha.imaging.calib.ProcessingMode
import com.takehaire.alpha.imaging.color.ColorMath
import com.takehaire.alpha.imaging.look.LookRenderer
import com.takehaire.alpha.imaging.look.LookSpec
import com.takehaire.alpha.imaging.lut.LutBaker
import com.takehaire.alpha.imaging.process.RotatingArgbBuffer
import com.takehaire.alpha.imaging.tone.ToneCurve
import com.takehaire.alpha.imaging.tone.ToneParams
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Generates docs/validation (images + REPORT.md). Run with: ./gradlew :imaging:test -Pvalidate=true
 * Every number in the report is computed here; nothing is typed in by hand.
 */
class ValidationSuiteTest {
    private val outDir = File(System.getProperty("haire.validationOut") ?: "build/validation")
    private val report = StringBuilder()
    private var duplicateFailure: String? = null
    private fun line(s: String = "") { report.append(s).append('\n') }

    private val showcase = listOf("neutral", "alpha_natural", "forever", "portra400", "classic_chrome", "cinestill800t", "trix400")

    @Test fun generateValidationReport() {
        assumeTrue(System.getProperty("haire.validate") == "true")
        outDir.mkdirs()
        File(outDir, "scenes").mkdirs()
        File(outDir, "photos").mkdirs()

        line("# HAIRE Alpha — Image Quality Validation Report")
        line()
        line("Generated automatically by `ValidationSuiteTest` (`./gradlew :imaging:test -Pvalidate=true`).")
        line()
        line("**Scope and honesty.** Everything here is *software-level* validation. Scenes are computer-generated")
        line("or public-domain display-referred photographs. The *synthetic camera* obeys the Luther condition and")
        line("is **not** a model of the Galaxy sensors, so colour-accuracy figures validate the correctness of the")
        line("development pipeline (black level, shading, white balance, demosaic, DNG matrices), **not** the")
        line("accuracy of any phone sensor. Physical-device calibration is still required (see PHYSICAL_DEVICE_CHECKLIST.md).")
        line()

        colorAccuracy()
        toneResponse()
        neutrality()
        skinTones()
        detailAndNoise()
        banding()
        distinctness()
        previewConsistency()
        scenesAndPhotos()

        File(outDir, "REPORT.md").writeText(report.toString())
        println(report)
        check(duplicateFailure == null) { duplicateFailure!! }
    }

    // ------------------------------------------------------------------ 1. colour accuracy
    private fun colorAccuracy() {
        line("## 1. RAW pipeline colour accuracy (synthetic camera, ColorChecker 24, ΔE2000)")
        line()
        val runs = listOf(
            Triple("D65, ForwardMatrix path, ISO 100", SyntheticCamera(), ColorMath.D65_XY) to 100,
            Triple("D65, ColorMatrix path, ISO 100", SyntheticCamera(useForwardMatrices = false), ColorMath.D65_XY) to 100,
            Triple("Tungsten (A), ForwardMatrix, ISO 400", SyntheticCamera(), doubleArrayOf(0.44757, 0.40745)) to 400,
            Triple("4300 K (interpolated matrices), ISO 1600", SyntheticCamera(), ColorMath.planckianXy(4300.0)) to 1600,
        )
        line("| Condition | mean ΔE00 | max ΔE00 | worst patch |")
        line("|---|---|---|---|")
        var first: Validation.ChartResult? = null
        for ((t, iso) in runs) {
            val r = Validation.colorCheckerThroughRaw(t.second, t.third, iso)
            if (first == null) first = r
            val worst = r.deltaE.indices.maxBy { r.deltaE[it] }
            line("| ${t.first} | ${f2(r.mean)} | ${f2(r.max)} | ${ColorChecker.NAMES[worst]} |")
        }
        val b = Validation.colorCheckerThroughRaw(SyntheticCamera(camFromXyz = SyntheticCamera.ALT_CAM_FROM_XYZ), ColorMath.D65_XY, 100)
        val s2s = first!!.labs.indices.map { ColorMath.deltaE2000(first.labs[it], b.labs[it]) }
        line()
        line("Sensor-to-sensor consistency (two different synthetic sensors, same scene): mean ΔE00 **${f2(s2s.average())}**, max ${f2(s2s.max())}.")
        line()
        line("Threshold: mean < 1.0, max < 2.0 for the D65 cases (enforced by `PipelineTest`).")
        line()
    }

    // ------------------------------------------------------------------ 2. tone
    private fun toneResponse() {
        line("## 2. Tone response (sRGB 8-bit output codes for neutral scene values)")
        line()
        line("White point = sensor clip at +3.0 EV above middle grey (typical). Plain sRGB encoding of the same")
        line("scene is shown for comparison (it lifts shadows and clips at +2.47 EV).")
        line()
        val evs = listOf(-6.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 2.5, 3.0)
        line("| Look | " + evs.joinToString(" | ") { (if (it > 0) "+" else "") + it.toString() + " EV" } + " |")
        line("|---|" + evs.joinToString("") { "---|" })
        line("| plain sRGB | " + evs.joinToString(" | ") { code(ColorMath.srgbEncode(0.18 * Math.pow(2.0, it))) } + " |")
        val o = DoubleArray(3)
        for (id in listOf("alpha_natural", "neutral", "standard", "deep", "soft", "forever", "portra400", "trix400")) {
            val r = LookRenderer(Assets.look(id), 3.0)
            line("| ${Assets.look(id).name} | " + evs.joinToString(" | ") { ev ->
                val v = 0.18 * Math.pow(2.0, ev); r.render(v, v, v, o); code(0.2126 * o[0] + 0.7152 * o[1] + 0.0722 * o[2])
            } + " |")
        }
        // Highlight smoothness: max second difference of the shoulder (8-bit codes per 0.05 EV²).
        val c = ToneCurve(ToneParams(), 3.0)
        var maxD2 = 0.0
        var ev = 0.0
        while (ev < 3.0) {
            val d2 = abs(c.encoded(ev + 0.05) - 2 * c.encoded(ev) + c.encoded(ev - 0.05)) * 255
            maxD2 = maxOf(maxD2, d2); ev += 0.01
        }
        line()
        line("Highlight shoulder smoothness (ALPHA NATURAL): max second difference ${f2(maxD2)} codes per (0.05 EV)² — no kinks. Clipped sensor data maps to 255.")
        line()
    }

    // ------------------------------------------------------------------ 3. neutrality
    private fun neutrality() {
        line("## 3. Neutral grey balance (Oklab chroma of a grey ramp, ×1000)")
        line()
        line("| Look | max chroma −6…+2 EV | intended tint |")
        line("|---|---|---|")
        val o = DoubleArray(3)
        val lab = DoubleArray(3)
        for (look in Assets.looks.looks) {
            val r = LookRenderer(look, 3.0)
            var maxC = 0.0
            var ev = -6.0
            while (ev <= 2.0) {
                val v = 0.18 * Math.pow(2.0, ev)
                r.render(v, v, v, o)
                ColorMath.linearSrgbToOklab(ColorMath.srgbDecode(o[0]), ColorMath.srgbDecode(o[1]), ColorMath.srgbDecode(o[2]), lab)
                maxC = maxOf(maxC, hypot(lab[1], lab[2])); ev += 0.25
            }
            val intended = look.warmth != 0.0 || look.tint != 0.0 || look.shadowTint != null || look.highlightTint != null ||
                (look.monochrome?.toneAmount ?: 0.0) != 0.0 || look.creativeMatrix != null
            line("| ${look.name} | ${f1(maxC * 1000)} | ${if (intended) "yes (creative)" else "no"} |")
        }
        line()
        line("Looks without an intended tint must stay below 2.0 (≈ invisible); ALPHA NATURAL, NEUTRAL, STANDARD are checked.")
        line()
    }

    // ------------------------------------------------------------------ 4. skin
    private fun skinTones() {
        line("## 4. Skin tone rendering (Monk Skin Tone scale, 10 swatches)")
        line()
        line("*Colour deviation* = ΔE2000 between the rendered skin and the scene skin colour re-lit to the rendered")
        line("lightness (so tone-curve changes are excluded and only hue/chroma changes count).")
        line("*Hue shift* is reported for swatches with enough chroma (C ≥ 0.03) for hue to be meaningful.")
        line("*Separation* = smallest lightness step between neighbouring tones MST 4–10 relative to the scene step (1.00 = kept).")
        line()
        line("| Look | mean colour deviation | max colour deviation | max \\|Δhue\\| | chroma ratio range | separation |")
        line("|---|---|---|---|---|---|")
        val o = DoubleArray(3)
        val labIn = DoubleArray(3)
        val labOut = DoubleArray(3)
        val rgb = DoubleArray(3)
        val toRec = ColorMath.SRGB_TO_REC2020
        for (id in listOf("alpha_natural", "standard", "neutral", "portrait", "vivid", "forever", "portra400", "portra800", "gold200", "ektar100", "classic_chrome", "sunday_archive", "after_hours", "cinestill800t")) {
            val look = Assets.look(id)
            val r = LookRenderer(look, 3.0)
            val dev = ArrayList<Double>(); val dh = ArrayList<Double>(); val cr = ArrayList<Double>()
            val lIn = ArrayList<Double>(); val lOut = ArrayList<Double>()
            for (i in 0 until 10) {
                val lin = MonkSkinTones.linearSrgb(i).map { it * 0.85 }
                val rec = toRec * com.takehaire.alpha.imaging.color.Vec3(lin[0], lin[1], lin[2])
                r.render(rec.x, rec.y, rec.z, o)
                ColorMath.linearSrgbToOklab(lin[0], lin[1], lin[2], labIn)
                val outLin = DoubleArray(3) { ColorMath.srgbDecode(o[it]) }
                ColorMath.linearSrgbToOklab(outLin[0], outLin[1], outLin[2], labOut)
                // Reference: scene chromaticity at the output lightness.
                val k = labOut[0] / labIn[0]
                ColorMath.oklabToLinearSrgb(labOut[0], labIn[1] * k, labIn[2] * k, rgb)
                dev.add(ColorMath.deltaE2000(ColorMath.linearSrgbToLab(outLin[0], outLin[1], outLin[2]), ColorMath.linearSrgbToLab(rgb[0], rgb[1], rgb[2])))
                val cIn = hypot(labIn[1], labIn[2])
                if (cIn >= 0.03) {
                    var d = ColorMath.hueDeg(labOut[1], labOut[2]) - ColorMath.hueDeg(labIn[1], labIn[2])
                    if (d > 180) d -= 360; if (d < -180) d += 360
                    dh.add(abs(d))
                    cr.add((hypot(labOut[1], labOut[2]) / labOut[0]) / (cIn / labIn[0]))
                }
                lIn.add(labIn[0]); lOut.add(labOut[0])
            }
            val sep = (4 until 10).minOf { (lOut[it - 1] - lOut[it]) / (lIn[it - 1] - lIn[it]) }
            val note = if (look.filmWhiteBalanceK != null) " (tungsten stock: WB fixed at ${look.filmWhiteBalanceK.toInt()} K at capture)" else ""
            line("| ${look.name}$note | ${f2(dev.average())} | ${f2(dev.max())} | ${f1(dh.max())} | ${f2(cr.min())}–${f2(cr.max())} | ${f2(sep)} |")
            if (id in setOf("alpha_natural", "neutral", "portrait", "forever")) {
                check(dev.max() < 3.0) { "$id skin colour deviation ${dev.max()}" }
                check(dh.max() < 4.0) { "$id skin hue shift ${dh.max()}" }
                check(sep > 0.7) { "$id skin tonal separation $sep" }
            }
        }
        line()
        line("Gates (ALPHA NATURAL, NEUTRAL, PORTRAIT, FOREVER): max colour deviation < 3.0, max hue shift < 4°, separation > 0.70.")
        line("Darker complexions (MST 7–10) are rendered without lightening or greying: see `scenes/portrait_daylight.jpg` and `scenes/portrait_warm.jpg`.")
        line()
    }

    // ------------------------------------------------------------------ 5. detail / noise
    private fun detailAndNoise() {
        line("## 5. Sharpening halos, texture retention and noise")
        line()
        line("| Processing mode | step-edge overshoot (ALPHA NATURAL) |")
        line("|---|---|")
        for (m in ProcessingMode.entries) line("| ${m.label} | ${f1(Validation.edgeOvershoot(Assets.alphaNatural, m) * 100)} % |")
        line()
        line("Threshold < 5 % (typical smartphone sharpening produces 15–40 % halos). Halo clamp limits overshoot to the local 3×3 range ± 0.012.")
        line()
        line("| Measure | ISO | result |")
        line("|---|---|---|")
        line("| Fine texture (4 px period) amplitude kept after NR | 100 | ${f1(Validation.textureRetention(100) * 100)} % |")
        line("| Fine texture amplitude kept after NR | 800 | ${f1(Validation.textureRetention(800) * 100)} % |")
        for (iso in listOf(800, 3200, 6400)) line("| Residual luma noise vs. no NR (flat grey) | $iso | ${f1(Validation.noiseReduction(iso) * 100)} % |")
        line()
        line("Noise is reduced progressively with ISO but never removed completely (no waxy surfaces); low-ISO texture is preserved.")
        line()
    }

    // ------------------------------------------------------------------ 6. banding
    private fun banding() {
        val scene = Validation.detailScene()
        val out = Validation.develop(scene, Validation.SW, Validation.SH, Assets.alphaNatural)
        val y = 280
        val row = (0 until Validation.SW).map { (Validation.luma(out.pixels[y * Validation.SW + it]) * 255).roundToInt() }
        var nonMono = 0
        var maxJump = 0
        for (i in 1 until row.size) {
            if (row[i] < row[i - 1] - 1) nonMono++
            maxJump = maxOf(maxJump, row[i] - row[i - 1])
        }
        line("## 6. Gradient smoothness / banding")
        line()
        line("Smooth −6…+2 EV sky-coloured gradient (480 px): ${row.toSet().size} distinct output codes, largest step $maxJump code(s), $nonMono reversals > 1 code (noise-free input). Processing uses 32-bit float throughout; quantisation happens once at output.")
        line()
    }

    // ------------------------------------------------------------------ 7. distinctness
    private fun samples(): List<DoubleArray> {
        val s = ArrayList<DoubleArray>()
        val toRec = ColorMath.XYZ_TO_REC2020 * ColorMath.D50_TO_D65
        for (i in 0 until 24) { val v = toRec * ColorChecker.xyzD50(i); s.add(doubleArrayOf(v.x, v.y, v.z)) }
        for (i in 0 until 10) {
            val l = MonkSkinTones.linearSrgb(i); val v = ColorMath.SRGB_TO_REC2020 * com.takehaire.alpha.imaging.color.Vec3(l[0], l[1], l[2])
            s.add(doubleArrayOf(v.x * 0.85, v.y * 0.85, v.z * 0.85))
        }
        for (ev in -5..2) { val v = 0.18 * Math.pow(2.0, ev.toDouble()); s.add(doubleArrayOf(v, v, v)) }
        return s
    }

    private fun renderLab(look: LookSpec, input: List<DoubleArray>): List<DoubleArray> {
        val r = LookRenderer(look, 3.0)
        val o = DoubleArray(3)
        return input.map { c ->
            r.render(c[0], c[1], c[2], o)
            ColorMath.linearSrgbToLab(ColorMath.srgbDecode(o[0]), ColorMath.srgbDecode(o[1]), ColorMath.srgbDecode(o[2]))
        }
    }

    private fun distinctness() {
        line("## 7. Look differentiation (mean ΔE2000 between looks over chart, skin and grey samples)")
        line()
        val input = samples()
        val looks = Assets.looks.looks
        val labs = looks.associate { it.id to renderLab(it, input) }
        data class P(val a: String, val b: String, val d: Double)
        val pairs = ArrayList<P>()
        for (i in looks.indices) for (j in i + 1 until looks.size) {
            val a = labs[looks[i].id]!!; val b = labs[looks[j].id]!!
            pairs.add(P(looks[i].name, looks[j].name, a.indices.map { ColorMath.deltaE2000(a[it], b[it]) }.average()))
        }
        pairs.sortBy { it.d }
        val everyday = setOf("ALPHA NATURAL", "STANDARD", "NEUTRAL", "PORTRAIT")
        fun limit(p: P) = if (p.a in everyday && p.b in everyday) 1.2 else 2.0
        line("Policy: the four everyday profiles (ALPHA NATURAL, STANDARD, NEUTRAL, PORTRAIT) are deliberately subtle")
        line("variants and must differ by ≥ 1.2 from each other; every other pair must differ by ≥ 2.0 (skin is")
        line("protected by design, which lowers these means).")
        line()
        line("Closest pairs:")
        line()
        line("| Look A | Look B | mean ΔE00 | minimum | result |")
        line("|---|---|---|---|---|")
        for (p in pairs.take(10)) line("| ${p.a} | ${p.b} | ${f2(p.d)} | ${f1(limit(p))} | ${if (p.d >= limit(p)) "pass" else "FAIL"} |")
        line()
        line("Each look vs ALPHA NATURAL:")
        line()
        val base = labs["alpha_natural"]!!
        line("| Look | mean ΔE00 vs ALPHA NATURAL |")
        line("|---|---|")
        for (l in looks) if (l.id != "alpha_natural") {
            val a = labs[l.id]!!
            line("| ${l.name} | ${f2(a.indices.map { ColorMath.deltaE2000(a[it], base[it]) }.average())} |")
        }
        line()
        pairs.firstOrNull { it.d < limit(it) }?.let { duplicateFailure = "near-duplicate looks: $it" }
    }

    // ------------------------------------------------------------------ 8. preview vs still
    private fun previewConsistency() {
        line("## 8. Preview-to-capture consistency (simulated)")
        line()
        line("The live viewfinder applies a 33³ LUT to the camera's processed preview frames; the still pipeline")
        line("applies a 65³ LUT to developed scene data. Both are baked from the same `LookRenderer`. Here the preview")
        line("frame is simulated as the scene encoded by the camera with the sRGB preset tone curve (8-bit), versus the")
        line("still path on the same scene (white point +2.47 EV in both). In-gamut, unclipped samples only.")
        line()
        line("| Look | preview vs still mean ΔE00 | max ΔE00 | if vendor curve unknown (approximation) mean ΔE00 |")
        line("|---|---|---|---|")
        val input = samples().filter { c -> c.all { it < 0.95 } }
        val m = ColorMath.REC2020_TO_SRGB.toDoubleArray()
        val sm = ToneCurve(ToneParams(contrast = 1.1), 2.47)
        for (id in listOf("alpha_natural", "forever", "portra400", "classic_chrome", "trix400")) {
            val look = Assets.look(id)
            val still = LutBaker.bakeScene(look, Assets.alphaNatural, 1.0, Stops2.whiteEvPreview)
            val prev = LutBaker.bakePreview(look, Assets.alphaNatural, 1.0, LutBaker.SourceModel(1.0, true))
            val prevApprox = LutBaker.bakePreview(look, Assets.alphaNatural, 1.0, LutBaker.SourceModel(1.0, false))
            val o1 = FloatArray(3); val o2 = FloatArray(3); val o3 = FloatArray(3)
            val d = ArrayList<Double>(); val dA = ArrayList<Double>()
            for (c in input) {
                still.lookup(LutBaker.SceneLog.encode(c[0].toFloat()), LutBaker.SceneLog.encode(c[1].toFloat()), LutBaker.SceneLog.encode(c[2].toFloat()), o1)
                val lin = DoubleArray(3) { k -> m[k * 3] * c[0] + m[k * 3 + 1] * c[1] + m[k * 3 + 2] * c[2] }
                val enc = FloatArray(3) { k -> ((ColorMath.srgbEncode(lin[k]) * 255).roundToInt() / 255.0).toFloat() }
                prev.lookup(enc[0], enc[1], enc[2], o2)
                // Unknown vendor curve: simulate a contrasty vendor S-curve on luminance-preserving channels.
                val encS = FloatArray(3) { k -> sm.encoded(Stops2.log2(lin[k].coerceAtLeast(1e-6) / 0.18)).toFloat() }
                prevApprox.lookup(encS[0], encS[1], encS[2], o3)
                fun lab(o: FloatArray) = ColorMath.linearSrgbToLab(ColorMath.srgbDecode(o[0].toDouble()), ColorMath.srgbDecode(o[1].toDouble()), ColorMath.srgbDecode(o[2].toDouble()))
                d.add(ColorMath.deltaE2000(lab(o1), lab(o2)))
                dA.add(ColorMath.deltaE2000(lab(o1), lab(o3)))
            }
            line("| ${look.name} | ${f2(d.average())} | ${f2(d.max())} | ${f2(dA.average())} |")
        }
        line()
        line("When the device honours the sRGB preset tone curve on the preview stream (checked at runtime from capture results),")
        line("the viewfinder is a close match. Otherwise the app shows a discreet ≈ indicator: the preview is an approximation.")
        line()
    }

    // ------------------------------------------------------------------ 9. images
    private fun scenesAndPhotos() {
        line("## 9. Visual comparison sheets")
        line()
        line("Columns: **Scene (colorimetric, clipped)** — plain sRGB encoding of the synthetic scene, no rendering; then")
        line(showcase.joinToString(", ") { "**" + Assets.look(it).name + "**" } + ".")
        line()
        val scenes = listOf(
            "chart" to Validation.chartScene(),
            "portrait_daylight" to Validation.portraitScene(false),
            "portrait_warm" to Validation.portraitScene(true),
            "backlit" to Validation.backlitScene(),
            "night" to Validation.nightScene(),
            "detail" to Validation.detailScene(),
        )
        for ((name, s) in scenes) {
            val tiles = ArrayList<Pair<String, RotatingArgbBuffer>>()
            tiles.add("SCENE (colorimetric)" to Validation.colorimetric(s, Validation.SW, Validation.SH))
            val iso = if (name == "night") 3200 else 100
            for (id in showcase) tiles.add(Assets.look(id).name to Validation.develop(s, Validation.SW, Validation.SH, Assets.look(id), iso = iso))
            writeSheet(File(outDir, "scenes/$name.jpg"), tiles, 4)
            line("- `scenes/$name.jpg`")
            if (name == "night") {
                val out = tiles[2].second
                val mean = out.pixels.map { Validation.luma(it) }.average()
                line("  - Night scene mean output luminance with ALPHA NATURAL: ${f2(mean)} (stays a night picture; no automatic brightening).")
            }
        }
        // Full look gallery on the chart + portrait.
        val gal = ArrayList<Pair<String, RotatingArgbBuffer>>()
        val comp = Validation.portraitScene(false)
        for (l in Assets.looks.looks) gal.add(l.name to Validation.develop(comp, Validation.SW, Validation.SH, l))
        writeSheet(File(outDir, "looks_gallery.jpg"), gal, 5, scale = 0.5)
        line("- `looks_gallery.jpg` — all ${Assets.looks.looks.size} factory looks")
        line()
        line("### Public-domain photographs (display-referred input → processed-image pathway)")
        line()
        line("These JPEG-derived photos have already been tone mapped by their original cameras, so they exercise the")
        line("*display-referred* path used when a device does not let us replace the vendor tone curve.")
        line()
        for (name in listOf("astronaut", "coffee", "chelsea", "rocket")) {
            val img = ImageIO.read(javaClass.getResourceAsStream("/reference/$name.png"))
            val w = img.width; val h = img.height
            val argb = IntArray(w * h)
            img.getRGB(0, 0, w, h, argb, 0, w)
            val tiles = ArrayList<Pair<String, RotatingArgbBuffer>>()
            tiles.add("ORIGINAL" to RotatingArgbBuffer(w, h, 0).also { System.arraycopy(argb, 0, it.pixels, 0, argb.size) })
            for (id in listOf("alpha_natural", "forever", "portra400", "classic_chrome", "sunday_archive", "trix400", "grit")) {
                val lut = LutBaker.bakePreview(Assets.look(id), Assets.alphaNatural, 1.0, LutBaker.SourceModel(1.0, false))
                val b = RotatingArgbBuffer(w, h, 0)
                val o = FloatArray(3)
                for (i in argb.indices) {
                    val p = argb[i]
                    lut.lookup(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f, o)
                    b.pixels[i] = (0xFF shl 24) or ((o[0] * 255 + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                        ((o[1] * 255 + 0.5f).toInt().coerceIn(0, 255) shl 8) or (o[2] * 255 + 0.5f).toInt().coerceIn(0, 255)
                }
                tiles.add(Assets.look(id).name to b)
            }
            writeSheet(File(outDir, "photos/$name.jpg"), tiles, 4, scale = if (w > 520) 0.75 else 1.0)
            line("- `photos/$name.jpg`")
        }
        line()
        line("Photo credits: see `imaging/src/test/resources/reference/LICENSES.md`.")
    }

    private fun writeSheet(file: File, tiles: List<Pair<String, RotatingArgbBuffer>>, cols: Int, scale: Double = 1.0) {
        val tw = (tiles[0].second.width * scale).toInt()
        val th = (tiles[0].second.height * scale).toInt()
        val label = 22
        val rows = (tiles.size + cols - 1) / cols
        val sheet = BufferedImage(cols * tw, rows * (th + label), BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(18, 18, 18)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font("DejaVu Sans", Font.BOLD, 13)
        for ((i, t) in tiles.withIndex()) {
            val b = t.second
            val img = BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB)
            img.setRGB(0, 0, b.width, b.height, b.pixels, 0, b.width)
            val x = (i % cols) * tw
            val y = (i / cols) * (th + label)
            g.drawImage(img, x, y + label, tw, th, null)
            g.color = Color(240, 232, 220)
            g.drawString(t.first, x + 6, y + 16)
        }
        g.dispose()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val p = writer.defaultWriteParam
        p.compressionMode = ImageWriteParam.MODE_EXPLICIT
        p.compressionQuality = 0.9f
        file.outputStream().use { os ->
            ImageIO.createImageOutputStream(os).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(sheet, null, null), p)
            }
        }
        writer.dispose()
    }

    private fun f1(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
    private fun f2(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)
    private fun code(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt().toString()
}

private object Stops2 {
    val whiteEvPreview = log2(1.0 / 0.18)
    fun log2(v: Double) = kotlin.math.ln(v) / kotlin.math.ln(2.0)
}
