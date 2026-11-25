# Physical-device validation checklist (SM-S948U)

Nothing below has been run on hardware yet; the build environment has no phone and no emulator.
Allow about 45 minutes. For each item, note **Pass / Fail / Notes**. If anything fails, export
**MENU › Setup › Diagnostics › Export** and include it with your notes.

## 0. Install and first launch
1. Install the APK (README). Launch: the camera should be live within about 1–2 s, with **no** onboarding.
2. Deny the camera permission once: a clear message with **Allow camera** appears. Allow: the preview starts.
3. Diagnostics: record the model, the camera ids, which ids are logical with which physical ids, **RAW developable**,
   **sRGB tone curve honoured**, the capture pathway, and the equivalent focal lengths.

## 1. Orientation and framing (the highest-risk unverified code)
4. Portrait: the preview is upright, not mirrored, and not stretched (a circle stays a circle).
5. Rotate to landscape both ways: the preview stays upright and the controls move to the right-hand panel.
6. Take a photo in each orientation: the saved photo is upright in Samsung Gallery and its framing matches the preview's 3:2 crop.
7. Tap the left and then the right side of the scene to focus: the correct object comes into focus each time.

## 2. Genuine exposure
8. **M mode** on a tripod with even light: take 1/100 ISO 100, then 1/50 ISO 100, then 1/100 ISO 200.
   Shots 2 and 3 should look about one stop brighter than shot 1. Check the EXIF exposure time and ISO match the dials (Playback › INFO).
9. Motion: photograph a spinning fan at 1/30 and at 1/1000. The blur should differ clearly.
10. **S mode** at 1/4000 indoors: the ISO dial turns red (under-exposure warning) and the photo is darker.
11. **A mode** in a dim room: the shutter holds 1/250 while ISO rises; once ISO reaches the maximum, the shutter slows.
12. Exposure compensation +1 and −1 in A mode: brighter and darker by about one stop.
13. Long exposure in M (1"): the preview shows "≈", the capture takes about 1 s and is not black.

## 3. Image quality
14. A daylight portrait of a person with deeper skin (if possible): natural, not grey or orange, and texture kept. Compare with the Samsung camera app.
15. A backlit scene with sky: the highlights roll off smoothly, the sky isn't neon, and the shadows stay dark.
16. A night street at ISO 3200: it still looks like night, with fine grain but no waxy or smeared surfaces.
17. Fine architecture and text at 100% (double-tap in Playback): no bright or dark halos along edges.
18. The same scene in ALPHA NATURAL, FOREVER, PORTRA 400, CLASSIC CHROME and TRI-X: each looks clearly different.
19. Compare preview vs. saved photo with Setting Effect on: colour and brightness should be close. Record any consistent offset.
20. Switch between virtual lenses that use different physical cameras (24 → 85 mm): the colour should stay consistent.

## 4. Lenses
21. Camera bag: record which lenses show Native, ◦ (moderate crop), • (substantial crop) or greyed out.
22. Mount 35 mm: the framing matches a 35 mm field of view, and the prime does not zoom.
23. Mount 24–70 mm: the zoom ring moves only between 24 and 70 mm. Watch whether the physical camera switches (Diagnostics: active physical camera).

## 5. Files and Gallery
24. JPEG: it appears in Samsung Gallery › Albums › **HAIRE Alpha**, opens in Lightroom Mobile via Share, and its EXIF has the real f-number and focal length.
25. RAW+JPEG: the DNG opens in Lightroom with correct orientation and plausible colour, and has no look applied.
26. Shoot 10 frames quickly: there are no crashes and no missing or duplicate files (the shutter shows a pending count).
27. Fill storage to under 150 MB: the app shows a clear message instead of failing silently.

## 6. Interruptions and recovery
28. Press Home during processing, then return: the photo is still saved and the camera reopens.
29. Receive a call while the camera is open, then return: the camera recovers (or shows "Tap to retry").
30. Open the Samsung camera app while HAIRE Alpha is in the background, then switch back: it reconnects.
31. Lock and unlock the screen 5 times: the preview comes back every time.
32. Force-stop, then relaunch: your previous mode, lens, look and settings are restored.

## 7. Measurements to send back (they improve calibration)
- Diagnostics export (with "Learned RAW baseline" after about 20 photos in A mode).
- Optionally: one RAW+JPEG of a ColorChecker in daylight. That enables measured, per-sensor colour calibration.
- Optionally: matching scenes from your A7C II (JPEG + ARW) and the phone (DNG) for aesthetic matching.
