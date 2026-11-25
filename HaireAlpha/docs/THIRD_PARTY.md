# Third-party software and data

| Component | License |
|---|---|
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX (core, activity, lifecycle, DataStore, ExifInterface, Compose, Material3) | Apache-2.0 |
| Robolectric, JUnit (tests only) | MIT / EPL-1.0 |
| ColorChecker Classic L*a*b* reference values (numeric data, tests) | Published measurement data |
| Monk Skin Tone Scale swatch values (tests) | CC BY 4.0, Google (Dr. Ellis Monk) |
| Reference photos in `imaging/src/test/resources/reference` (tests) | Public domain / CC0, see LICENSES.md there |

All imaging algorithms (DNG colour model per the Adobe DNG Specification; Malvar–He–Cutler demosaic;
Oklab by B. Ottosson; CIEDE2000; Bradford CAT; guided filter by He et al.) are implemented from their
published descriptions. No proprietary camera-maker or film-stock colour data is used.
No network access, analytics or accounts are used by the app.
