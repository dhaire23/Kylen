# Build and test results (actual)

Recorded on 2026-11-25 in the cloud build container: 4 vCPU, OpenJDK 21.0.13, Gradle 8.14.3, AGP 8.13.2,
Kotlin 2.2.21, compileSdk/targetSdk 36, minSdk 29. Command:

```
./gradlew clean
./gradlew :imaging:test -Pvalidate=true :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```
Result: **BUILD SUCCESSFUL**.

| Suite | Kind | Tests | Failures |
|---|---|---|---|
| CoreMathTest | JVM: colour maths, CIEDE2000 reference data, CCT, tone curve, LUT, .cube validation | 16 | 0 |
| PipelineTest | JVM: DNG colour model, RAW development, looks, LUT accuracy, exposure program, lens maths | 33 | 0 |
| ValidationSuiteTest | JVM: generates `docs/validation/` and enforces the image-quality gates | 1 | 0 |
| PerformanceBenchmarkTest | JVM: 10.7 MP RAW development timing | 1 | 0 |
| AppLogicTest | Robolectric (SDK 35) with a simulated camera: settings, defaults, memory recall, lens planning, Fn, rotation sink, recipes | 14 | 0 |
| UiScreenshotTest | Robolectric native graphics: renders 8 real Compose screens to `docs/screenshots/` | 8 | 0 |
| BokehTest | JVM: subject untouched, no colour halo, deeper blur away from subject, low-confidence masks skipped | 4 | 0 |
| **Total** | | **77** | **0** |

- **Android lint** (debug and release): 0 errors, 6 warnings (KTX-style suggestions), 2 hints.
- **APKs (rc2)**: release 50.3 MB, arm64-v8a only (ML Kit's offline model and native code), R8 off for this RC, signed with
  the sideload key: `release/HAIRE-Alpha-1.0.0-rc2.apk` (SHA-256 in the `.sha256` file).
- **Performance (JVM, this container, not the phone):** RAW development of 10.7 MP takes 2.1–3.7 s across runs (it varies with
  container load), plus a 0.3–0.5 s one-off 65³ LUT bake per look and white point.

## What these tests do *not* prove
- No test ran against a real camera HAL: Camera2 sessions, capture, the GL viewfinder, MediaStore on Samsung and
  orientation are **unverified on hardware**. See `PHYSICAL_DEVICE_CHECKLIST.md`.
- The colour-accuracy figures use a *synthetic* camera and validate pipeline correctness, not the Galaxy sensors.
- The UI screenshots are rendered without a camera feed; the viewfinder shows a placeholder gradient.
