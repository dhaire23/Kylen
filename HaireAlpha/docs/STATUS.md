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
- [x] M1 Foundation: build env, project skeleton, capability discovery
- [x] M2 Core camera: Camera2 state machine, genuine exposure (AUTO/P/A/S/M), focus (AF-S/AF-C/MF/touch/areas), WB (AWB, presets, Kelvin+tint, shift grid), capture, MediaStore
- [x] M3 Imaging engine (RAW development, YUV/JPEG pathways, DNG colour, NR, sharpening)
- [x] M4 Factory calibration on synthetic and public data: `docs/validation/REPORT.md`
- [x] M5 Interface: dials, EV scale, Fn (customisable), MENU (8 tabs), playback, diagnostics, DISP, landscape layout
- [x] M6 25 looks (Creative 14, Film 7, Signature 4), intensity, favourites, live thumbnails
- [x] M7 Virtual lenses (14 profiles), quality-aware sensor/crop planning, zoom ring, crop indicators
- [x] M8 RAW+JPEG/DNG, Memory Recall M1–M3, recipes (create/duplicate/rename/delete, import/export), .cube import, grid/level/histogram/zebras/peaking
- [x] M9 Hardening: 73 tests, lint clean, startup race and GPU-failure fallbacks, bounded capture queue
- [x] M10 Release candidate APK + docs
- [ ] Physical-device validation (`PHYSICAL_DEVICE_CHECKLIST.md`)

## Known limitations
- **Virtual bokeh / computational depth: not implemented.** The UI says so wherever aperture appears. The virtual aperture
  is recorded in metadata only. A natural result needs depth data or a segmentation model that this build doesn't include.
- Not yet run on the SM-S948U. The highest-risk unverified areas are preview orientation in landscape, touch-focus coordinate
  mapping, logical multi-camera RAW (physical-camera switching), and DngCreator with logical-camera results.
- Samsung's own multi-frame processing isn't available to third-party Camera2 apps; COMPUTATIONAL mode is single-frame
  (vendor NR plus HAIRE local tone).
- HEIF output isn't offered (JPEG/DNG only).
- Face detection uses the camera's own detector for AF priority; no subject tracking is claimed.
- R8 shrinking is disabled for this RC.
- Favourites need Android 11+; deleting photos made before a reinstall must be done in Gallery.

## Next steps (after the device checklist)
1. Fix anything the checklist finds (orientation/focus mapping first).
2. Use the Diagnostics export plus a ColorChecker DNG to add a measured per-sensor profile (`calibration.json`).
3. Optional: computational bokeh (depth / segmentation), enable R8, and a subject-tracking AF overlay.
