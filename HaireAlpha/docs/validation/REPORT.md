# HAIRE Alpha — Image Quality Validation Report

Generated automatically by `ValidationSuiteTest` (`./gradlew :imaging:test -Pvalidate=true`).

**Scope and honesty.** Everything here is *software-level* validation. Scenes are computer-generated
or public-domain display-referred photographs. The *synthetic camera* obeys the Luther condition and
is **not** a model of the Galaxy sensors, so colour-accuracy figures validate the correctness of the
development pipeline (black level, shading, white balance, demosaic, DNG matrices), **not** the
accuracy of any phone sensor. Physical-device calibration is still required (see PHYSICAL_DEVICE_CHECKLIST.md).

## 1. RAW pipeline colour accuracy (synthetic camera, ColorChecker 24, ΔE2000)

| Condition | mean ΔE00 | max ΔE00 | worst patch |
|---|---|---|---|
| D65, ForwardMatrix path, ISO 100 | 0.06 | 0.17 | black 2 |
| D65, ColorMatrix path, ISO 100 | 0.06 | 0.17 | black 2 |
| Tungsten (A), ForwardMatrix, ISO 400 | 0.15 | 0.48 | black 2 |
| 4300 K (interpolated matrices), ISO 1600 | 0.55 | 1.44 | black 2 |

Sensor-to-sensor consistency (two different synthetic sensors, same scene): mean ΔE00 **0.02**, max 0.07.

Threshold: mean < 1.0, max < 2.0 for the D65 cases (enforced by `PipelineTest`).

## 2. Tone response (sRGB 8-bit output codes for neutral scene values)

White point = sensor clip at +3.0 EV above middle grey (typical). Plain sRGB encoding of the same
scene is shown for comparison (it lifts shadows and clips at +2.47 EV).

| Look | -6.0 EV | -4.0 EV | -3.0 EV | -2.0 EV | -1.0 EV | 0.0 EV | +1.0 EV | +2.0 EV | +2.5 EV | +3.0 EV |
|---|---|---|---|---|---|---|---|---|---|---|
| plain sRGB | 9 | 27 | 41 | 60 | 85 | 118 | 162 | 221 | 255 | 255 |
| ALPHA NATURAL | 6 | 19 | 33 | 53 | 82 | 117 | 166 | 222 | 243 | 255 |
| NEUTRAL | 11 | 28 | 43 | 63 | 88 | 117 | 164 | 223 | 245 | 255 |
| STANDARD | 3 | 13 | 25 | 46 | 77 | 117 | 167 | 221 | 242 | 255 |
| DEEP | 1 | 6 | 16 | 35 | 67 | 103 | 156 | 218 | 242 | 255 |
| SOFT | 20 | 38 | 53 | 73 | 99 | 128 | 172 | 224 | 243 | 251 |
| FOREVER | 2 | 11 | 23 | 45 | 77 | 115 | 166 | 224 | 245 | 255 |
| PORTRA 400 INSPIRED | 8 | 23 | 38 | 58 | 87 | 120 | 168 | 225 | 245 | 255 |
| TRI-X 400 INSPIRED | 1 | 6 | 16 | 36 | 71 | 117 | 171 | 224 | 243 | 255 |

Highlight shoulder smoothness (ALPHA NATURAL): max second difference 0.81 codes per (0.05 EV)² — no kinks. Clipped sensor data maps to 255.

## 3. Neutral grey balance (Oklab chroma of a grey ramp, ×1000)

| Look | max chroma −6…+2 EV | intended tint |
|---|---|---|
| ALPHA NATURAL | 0.0 | no |
| STANDARD | 0.0 | no |
| NEUTRAL | 0.0 | no |
| PORTRAIT | 3.1 | yes (creative) |
| VIVID | 0.1 | no |
| LANDSCAPE | 5.5 | yes (creative) |
| DEEP | 3.0 | yes (creative) |
| SOFT | 2.2 | yes (creative) |
| MONOCHROME | 0.0 | no |
| HIGH CONTRAST MONO | 0.0 | no |
| CLASSIC CHROME INSPIRED | 6.0 | yes (creative) |
| CLASSIC NEGATIVE INSPIRED | 18.0 | yes (creative) |
| CINEMA | 19.9 | yes (creative) |
| SOFT MATTE | 12.1 | yes (creative) |
| PORTRA 400 INSPIRED | 12.7 | yes (creative) |
| PORTRA 800 INSPIRED | 14.4 | yes (creative) |
| GOLD 200 INSPIRED | 17.7 | yes (creative) |
| EKTAR 100 INSPIRED | 12.8 | yes (creative) |
| CINESTILL 800T INSPIRED | 16.2 | yes (creative) |
| TRI-X 400 INSPIRED | 0.0 | no |
| HP5 PLUS 400 INSPIRED | 9.0 | yes (creative) |
| FOREVER | 8.4 | yes (creative) |
| SUNDAY ARCHIVE | 15.7 | yes (creative) |
| AFTER HOURS | 13.6 | yes (creative) |
| GRIT | 0.0 | no |

Looks without an intended tint must stay below 2.0 (≈ invisible); ALPHA NATURAL, NEUTRAL, STANDARD are checked.

## 4. Skin tone rendering (Monk Skin Tone scale, 10 swatches)

*Colour deviation* = ΔE2000 between the rendered skin and the scene skin colour re-lit to the rendered
lightness (so tone-curve changes are excluded and only hue/chroma changes count).
*Hue shift* is reported for swatches with enough chroma (C ≥ 0.03) for hue to be meaningful.
*Separation* = smallest lightness step between neighbouring tones MST 4–10 relative to the scene step (1.00 = kept).

| Look | mean colour deviation | max colour deviation | max \|Δhue\| | chroma ratio range | separation |
|---|---|---|---|---|---|
| ALPHA NATURAL | 0.72 | 1.23 | 0.5 | 0.86–1.11 | 0.94 |
| STANDARD | 1.41 | 2.88 | 1.1 | 0.89–1.28 | 0.91 |
| NEUTRAL | 1.21 | 2.06 | 0.3 | 0.77–0.91 | 0.86 |
| PORTRAIT | 0.50 | 1.59 | 1.0 | 0.82–1.02 | 0.87 |
| VIVID | 2.21 | 5.13 | 2.2 | 0.94–1.54 | 0.88 |
| FOREVER | 0.61 | 1.04 | 2.6 | 0.91–1.07 | 0.97 |
| PORTRA 400 INSPIRED | 0.95 | 1.74 | 3.7 | 0.91–1.06 | 0.92 |
| PORTRA 800 INSPIRED | 2.57 | 4.75 | 2.7 | 1.04–1.47 | 0.98 |
| GOLD 200 INSPIRED | 2.97 | 4.21 | 3.6 | 1.14–1.37 | 0.88 |
| EKTAR 100 INSPIRED | 2.78 | 5.32 | 2.2 | 1.02–1.55 | 0.91 |
| CLASSIC CHROME INSPIRED | 1.78 | 3.24 | 1.8 | 0.65–0.97 | 0.93 |
| SUNDAY ARCHIVE | 1.92 | 2.65 | 6.8 | 0.89–1.18 | 0.84 |
| AFTER HOURS | 1.00 | 3.11 | 1.6 | 0.83–0.98 | 1.01 |
| CINESTILL 800T INSPIRED (tungsten stock: WB fixed at 3200 K at capture) | 2.12 | 5.49 | 8.7 | 0.81–1.05 | 0.99 |

Gates (ALPHA NATURAL, NEUTRAL, PORTRAIT, FOREVER): max colour deviation < 3.0, max hue shift < 4°, separation > 0.70.
Darker complexions (MST 7–10) are rendered without lightening or greying: see `scenes/portrait_daylight.jpg` and `scenes/portrait_warm.jpg`.

## 5. Sharpening halos, texture retention and noise

| Processing mode | step-edge overshoot (ALPHA NATURAL) |
|---|---|
| NATURAL | 0.0 % |
| PURE | 0.0 % |
| COMPUTATIONAL | 1.0 % |

Threshold < 5 % (typical smartphone sharpening produces 15–40 % halos). Halo clamp limits overshoot to the local 3×3 range ± 0.012.

| Measure | ISO | result |
|---|---|---|
| Fine texture (4 px period) amplitude kept after NR | 100 | 99.8 % |
| Fine texture amplitude kept after NR | 800 | 97.9 % |
| Residual luma noise vs. no NR (flat grey) | 800 | 78.7 % |
| Residual luma noise vs. no NR (flat grey) | 3200 | 70.5 % |
| Residual luma noise vs. no NR (flat grey) | 6400 | 67.3 % |

Noise is reduced progressively with ISO but never removed completely (no waxy surfaces); low-ISO texture is preserved.

## 6. Gradient smoothness / banding

Smooth −6…+2 EV sky-coloured gradient (480 px): 206 distinct output codes, largest step 1 code(s), 0 reversals > 1 code (noise-free input). Processing uses 32-bit float throughout; quantisation happens once at output.

## 7. Look differentiation (mean ΔE2000 between looks over chart, skin and grey samples)

Policy: the four everyday profiles (ALPHA NATURAL, STANDARD, NEUTRAL, PORTRAIT) are deliberately subtle
variants and must differ by ≥ 1.2 from each other; every other pair must differ by ≥ 2.0 (skin is
protected by design, which lowers these means).

Closest pairs:

| Look A | Look B | mean ΔE00 | minimum | result |
|---|---|---|---|---|
| NEUTRAL | PORTRAIT | 1.25 | 1.2 | pass |
| ALPHA NATURAL | PORTRAIT | 1.43 | 1.2 | pass |
| ALPHA NATURAL | NEUTRAL | 1.52 | 1.2 | pass |
| ALPHA NATURAL | STANDARD | 1.64 | 1.2 | pass |
| PORTRAIT | PORTRA 400 INSPIRED | 2.01 | 2.0 | pass |
| STANDARD | LANDSCAPE | 2.10 | 2.0 | pass |
| ALPHA NATURAL | FOREVER | 2.10 | 2.0 | pass |
| HIGH CONTRAST MONO | TRI-X 400 INSPIRED | 2.12 | 2.0 | pass |
| CLASSIC NEGATIVE INSPIRED | FOREVER | 2.16 | 2.0 | pass |
| CLASSIC CHROME INSPIRED | FOREVER | 2.20 | 2.0 | pass |

Each look vs ALPHA NATURAL:

| Look | mean ΔE00 vs ALPHA NATURAL |
|---|---|
| STANDARD | 1.64 |
| NEUTRAL | 1.52 |
| PORTRAIT | 1.43 |
| VIVID | 3.82 |
| LANDSCAPE | 2.34 |
| DEEP | 3.83 |
| SOFT | 4.03 |
| MONOCHROME | 12.38 |
| HIGH CONTRAST MONO | 13.85 |
| CLASSIC CHROME INSPIRED | 2.75 |
| CLASSIC NEGATIVE INSPIRED | 2.68 |
| CINEMA | 3.58 |
| SOFT MATTE | 3.38 |
| PORTRA 400 INSPIRED | 2.66 |
| PORTRA 800 INSPIRED | 2.93 |
| GOLD 200 INSPIRED | 4.27 |
| EKTAR 100 INSPIRED | 3.41 |
| CINESTILL 800T INSPIRED | 2.83 |
| TRI-X 400 INSPIRED | 13.05 |
| HP5 PLUS 400 INSPIRED | 12.89 |
| FOREVER | 2.10 |
| SUNDAY ARCHIVE | 4.43 |
| AFTER HOURS | 4.03 |
| GRIT | 14.17 |

## 8. Preview-to-capture consistency (simulated)

The live viewfinder applies a 33³ LUT to the camera's processed preview frames; the still pipeline
applies a 65³ LUT to developed scene data. Both are baked from the same `LookRenderer`. Here the preview
frame is simulated as the scene encoded by the camera with the sRGB preset tone curve (8-bit), versus the
still path on the same scene (white point +2.47 EV in both). In-gamut, unclipped samples only.

| Look | preview vs still mean ΔE00 | max ΔE00 | if vendor curve unknown (approximation) mean ΔE00 |
|---|---|---|---|
| ALPHA NATURAL | 0.31 | 2.41 | 1.69 |
| FOREVER | 0.31 | 2.21 | 1.35 |
| PORTRA 400 INSPIRED | 0.34 | 2.30 | 1.21 |
| CLASSIC CHROME INSPIRED | 0.35 | 2.29 | 1.29 |
| TRI-X 400 INSPIRED | 0.09 | 1.48 | 0.93 |

When the device honours the sRGB preset tone curve on the preview stream (checked at runtime from capture results),
the viewfinder is a close match. Otherwise the app shows a discreet ≈ indicator: the preview is an approximation.

## 9. Visual comparison sheets

Columns: **Scene (colorimetric, clipped)** — plain sRGB encoding of the synthetic scene, no rendering; then
**NEUTRAL**, **ALPHA NATURAL**, **FOREVER**, **PORTRA 400 INSPIRED**, **CLASSIC CHROME INSPIRED**, **CINESTILL 800T INSPIRED**, **TRI-X 400 INSPIRED**.

- `scenes/chart.jpg`
- `scenes/portrait_daylight.jpg`
- `scenes/portrait_warm.jpg`
- `scenes/backlit.jpg`
- `scenes/night.jpg`
  - Night scene mean output luminance with ALPHA NATURAL: 0.18 (stays a night picture; no automatic brightening).
- `scenes/detail.jpg`
- `looks_gallery.jpg` — all 25 factory looks

### Public-domain photographs (display-referred input → processed-image pathway)

These JPEG-derived photos have already been tone mapped by their original cameras, so they exercise the
*display-referred* path used when a device does not let us replace the vendor tone curve.

- `photos/astronaut.jpg`
- `photos/coffee.jpg`
- `photos/chelsea.jpg`
- `photos/rocket.jpg`

Photo credits: see `imaging/src/test/resources/reference/LICENSES.md`.
