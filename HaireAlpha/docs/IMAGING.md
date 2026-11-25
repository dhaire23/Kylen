# Imaging pipeline and calibration

## Acquisition pathways (per physical camera, decided at runtime)

| Pathway | When | What HAIRE does |
|---|---|---|
| **A. RAW_SENSOR → HAIRE development** (default for NATURAL and PURE) | Camera advertises RAW, DNG colour matrices, a supported CFA **and** a lens-shading map | Full development, below. A vendor JPEG is captured alongside it as a safety fallback. |
| **B. YUV, minimally processed** (COMPUTATIONAL, or when A is impossible) | Always available | Requests `EDGE_MODE_OFF`, `NOISE_REDUCTION_MODE_MINIMAL` and the sRGB preset tone curve where supported, then applies the HAIRE look and HAIRE sharpening. **This is processed data, not RAW.** |
| **C. Vendor JPEG** | Only if YUV fails | Decoded, then the look is applied in display-referred mode. |

The pathway actually used is shown in the top bar and in Diagnostics, and is recorded in each photo's EXIF UserComment.
Nothing claims that manufacturer processing was bypassed unless RAW was used. Whether the tone-curve request was
honoured is read back from capture results, not assumed.

## RAW development (`imaging/.../raw`, `.../process`)

1. Black level per CFA position (dynamic black level from the result, else the characteristics pattern), white level, normalisation.
2. Lens shading: the per-capture gain map from `STATISTICS_LENS_SHADING_CORRECTION_MAP`, bilinear per channel. RAW_SENSOR is pre-shading, so this is required.
3. White balance gains before demosaic. Every channel clips at the level where the least-amplified channel saturates, so blown highlights render neutral. No reconstruction of clipped data is claimed.
4. Isolated hot-pixel suppression.
5. Demosaic: Malvar–He–Cutler (2004).
6. Colour: DNG spec chapter 6, with ForwardMatrix (or ColorMatrix plus Bradford), CameraCalibration, two-illuminant interpolation in inverse CCT, and an iterative white point from AsShotNeutral or a Kelvin/tint neutral. The working space is linear Rec.2020.
7. Exposure normalisation (see below), vignette, halation and local tone in scene-linear light.
8. Noise reduction driven by the sensor's own `SENSOR_NOISE_PROFILE` (variance = S·x + O). Luma uses a range-weighted 5×5 filter scaled to local noise sigma; chroma uses sparse 9×9 support. Some noise is always kept, so surfaces never go waxy.
9. **All colour in one 65³ 3D LUT** (tetrahedral), baked from `LookRenderer`.
10. Restrained luminance sharpening with a noise threshold and a halo clamp (overshoot limited to the local 3×3 range ± 0.012).
11. Grain scaled to image size, then a single 8-bit quantisation and JPEG at quality 95. Rotation happens while writing, so pixels are upright and EXIF orientation is 1.

Processing is striped and multi-threaded (a few MB per thread). Measured on the 4-core build container's JVM, a 10.7 MP 3:2
frame develops in about 2.1 s, plus about 0.3 s for a one-off 65³ LUT bake. Phone timing must be measured on the device.

## One colour transform for preview and stills

`LookRenderer` (scene-linear Rec.2020 → sRGB) applies these steps once each: creative balance and matrix; a filmic tone curve
(hue-preserving power-norm blended with per-channel, plus path-to-white); Oklab/Oklch colour work (saturation,
vibrance, chroma compression, hue bands, split tone); skin protection; perceptual gamut mapping into sRGB; and the sRGB encode.
It is baked into a 65³ LUT for stills and a 33³ GPU LUT for the viewfinder. The viewfinder LUT expects the camera's preview
(decoded with the sRGB curve when that curve is honoured, otherwise display-referred mode with a "≈" indicator).
The simulated mean preview-to-still difference is 0.3 ΔE00 (see the validation report).

### Tone curve
The toe is deeper than a plain sRGB encode, so shadows stay photographic. Middle grey maps to code ≈117. The highlight shoulder
reaches white **exactly at the sensor clip** (`whiteEv`), so clipped data is white rather than grey and any available
headroom becomes a smooth roll-off.

### Skin protection
The Monk Skin Tone swatches span Oklch hue 43–85° and chroma 0.01–0.07. Inside that region, the hue rotation and chroma
change of any look are bounded (±4°, ×0.88–1.12) in proportion to the look's `skinProtect`. Deep complexions therefore
cannot be pushed grey, ashy, orange or red.

## Exposure

The camera's AE is used **only as a light meter**. Its exposure product E = t·ISO·boost already includes exposure compensation.
`ExposureProgram` turns E into a real shutter and ISO:

- **A / P / AUTO**: Auto ISO holds the minimum shutter target. Bright light gets a faster shutter; when ISO hits its ceiling, the shutter is allowed to go slower.
- **S**: Your shutter, with ISO from the meter. If ISO is out of range the dial turns red.
- **M**: Your shutter and ISO. The meter comes from the GPU-measured preview luminance; optional Auto ISO in M.

The still is captured with AE off and post-RAW boost set to 100%, so the settings are genuine. M-mode preview uses
manual sensor values too. Exposures longer than 1/15 s are previewed at 1/15 s with equivalent gain, marked "≈".

### RAW brightness ("baseline exposure")
Camera2 provides no DNG BaselineExposure. In the auto modes, HAIRE anchors RAW brightness to what the camera's meter showed:
it compares the centre-weighted luminance of the preview (pre-look, linear) with the same region of the RAW. The result is
clamped to ±1.5 EV of the per-sensor baseline. That anchor is learned per camera id (moving average) and is the only thing
M mode uses, so manual exposure stays honest. Learned values are shown in Diagnostics.

## Calibration status: what is and is not measured

| Item | Source | Status |
|---|---|---|
| Camera → XYZ colour | Camera2 DNG metadata (per physical camera, per capture neutral) | **Metadata-derived** at runtime |
| Lens shading, black/white level, noise model | Per-capture result metadata | **Metadata-derived** |
| Pipeline correctness (black level, shading, WB, demosaic, matrices) | Synthetic Luther-condition camera, ColorChecker reference data | **Synthetic-validated**: mean ΔE00 0.06 (D65) |
| Look aesthetics, skin protection, tone curve | Published ColorChecker and Monk Skin Tone data, public-domain photos | **Aesthetic tuning**, not measurement |
| Accuracy of the actual Galaxy sensors | — | **Not yet measured.** Needs the physical checklist and, ideally, a ColorChecker shot. |

Calibration profiles (`calibration.json`) are independent of looks (`looks.json`). Both are versioned and can be updated
without code changes. Edit `tools/gen_assets.py`, then run `./gradlew :imaging:test -Pvalidate=true` to re-check the gates.

## Validation gates (enforced in tests)
- ColorChecker through RAW: mean ΔE00 < 1, max < 2. Sensor-to-sensor consistency is reported.
- Tone curves: monotone, slope-continuous at middle grey, white at the clip.
- LUT vs. direct renderer, realistic colours: mean < 0.5 code, p99 < 2 codes.
- Step-edge overshoot < 5% in every processing mode; texture kept ≥ 85% at ISO 100.
- Looks: no near-duplicates (pairwise mean ΔE00 ≥ 2.0; ≥ 1.2 within the everyday set).
- Skin (ALPHA NATURAL, NEUTRAL, PORTRAIT, FOREVER): colour deviation < 3 ΔE00, hue shift < 4°, tonal separation of deep tones > 0.7.
