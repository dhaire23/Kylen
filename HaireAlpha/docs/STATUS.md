# HAIRE Alpha — Implementation Status (continuity record)

Keep this file current: it is the hand-off record for long sessions / context compaction.

## Build environment
- JDK 21 (target 17), Gradle 8.14.3 wrapper, AGP 8.13.2, Kotlin 2.2.21, compileSdk 36, minSdk 29.
- Android SDK at /opt/android-sdk (`local.properties`). Requires network access to dl.google.com (Google Maven).
- No KVM in the build container → no emulator; UI verified with Robolectric screenshots.

## Modules
- `:imaging` — pure Kotlin/JVM imaging engine (colour science, DNG colour model, tone, looks, LUTs,
  RAW development, NR, sharpening, grain, halation, exposure program, lens maths, calibration assets).
  Fully unit tested on the JVM. Assets in `imaging/src/main/resources/haire/*.json`
  (generated from `tools/gen_assets.py` — edit the script, re-run it).
- `:app` — Android app (Camera2, GL preview, Compose UI, MediaStore, DataStore).

## Milestones
- [x] M1 Foundation: build env, project skeleton
- [x] M3 Imaging engine (JVM) — 49 tests passing
- [x] M4 Factory calibration on synthetic data — `docs/validation/REPORT.md`
- [ ] M2 Core camera (Camera2) — in progress
- [ ] M5 UI, M6 looks in app, M7 lenses in app, M8 advanced, M9 hardening, M10 release

## Key decisions
- One colour transform (`LookRenderer`) baked to LUTs for both preview (33³, input = camera preview) and
  still (65³, input = log-encoded scene-linear Rec.2020) → preview/still consistency by construction.
- Camera AE is used as a light meter only; `ExposureProgram` splits E = t·ISO into real shutter/ISO and the
  still is captured with AE off (genuine exposure control; S/A/M honour user settings).
- RAW pathway (our development) preferred when RAW + lens-shading map are available; else YUV with
  edge enhancement off, minimal NR and sRGB preset tone curve where supported.
- Looks must differ by ≥ 2.0 mean ΔE00 (everyday set ≥ 1.2); skin protection bounded in Oklch.

## Known limitations / unverified on hardware
- Nothing has been run on the physical SM-S948U yet. See docs/PHYSICAL_DEVICE_CHECKLIST.md.
