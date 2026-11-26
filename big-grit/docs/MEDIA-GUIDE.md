# Media guide — replacing photos & video

Everything visual is listed in **`config/media.config.js`**. Each slot already has a file name. You never have to touch code.

## The 3-step swap

1. **Export** the clip or photo to the spec below (or run `scripts/optimize-media.sh`, which does it for you).
2. **Name** it exactly as the slot's `src` says, e.g. `hero-01-desktop.mp4`.
3. **Drop** it into that folder (`media/hero/`) and redeploy (on Netlify: drag the folder in again).

The site checks which files exist when it loads. Missing files are skipped; present ones appear automatically. To use a different name or a CDN link, change the `src` text in `media.config.js` instead.

> While `showPlaceholders: true` (top of `media.config.js`), every slot without a file shows a dashed label with its file name and spec. **Set it to `false` before launch** — missing slots then fall back to the authentic photo or disappear cleanly.

## Slot map

| Where on the site | Slot (`media.config.js`) | File(s) to supply | Spec |
|---|---|---|---|
| Opening sequence | `heroSequence` (5 entries — add/remove/reorder freely) | `media/hero/hero-01-desktop.mp4` + `hero-01-mobile.mp4` + `hero-01-poster.jpg` (and 02, 03) | Desktop 1920×1080, mobile 1080×1920, H.264 MP4, **5–8 s, no audio, < 6 MB each** |
| Opening poster (instant first paint) | `heroPoster` | currently the authentic photo | ≥ 1600 px tall |
| Entrance flash-cut stills | `heroIntro.stills` (up to 4) | any photos, e.g. `media/hero/flash-01.jpg` | portrait 1600×2000+, strong and different from each other — currently two crops of the authentic photo |
| Philosophy — training film | `philosophy.training` | `media/training/training-01.mp4` (+ poster) | 4:5 vertical, 1600×2000, 6–10 s seamless loop, < 5 MB |
| Philosophy — detail | `philosophy.detail` | `media/details/detail-01.mp4` | 4:3, 1440×1080, 4–6 s loop, < 3 MB |
| "No shortcuts." band | `detailBand` | `media/details/detail-02.mp4` (+ `-mobile.mp4`) | 21:9 or 16:9, 6–10 s slow-motion detail, < 6 MB |
| Meet Delone — sticky frame | `founder.portrait` | `media/founder/delone-portrait.jpg` | 4:5, 1600×2000 JPG, < 500 KB |
| Chapter 01 The Field | `founder.chapters.0` | `chapter-01-the-field.jpg` | NFL-era photo — **confirm you have usage rights** |
| Chapter 02 The Setback | `founder.chapters.1` | `chapter-02-the-setback.jpg` | quiet, moody portrait |
| Chapter 03 The Rebuild | `founder.chapters.2` | `chapter-03-the-rebuild.jpg` | training / stage / IFBB |
| Chapter 04 The Coach | `founder.chapters.3` | `chapter-04-the-coach.jpg` | Delone coaching a client (with permission) |
| Founder film | `interview` | `media/interview/founder-film.mp4` **or** a YouTube/Vimeo link in `src` | 1080p, with audio, 60–180 s |
| Coaching rows (desktop hover image) | `coaching.inPerson / online / consult` | `media/editorial/coaching-*.jpg` | 4:5, 1200×1500 |
| Find Your Grit backdrop | `editorial.assessment` | `media/editorial/find-your-grit.jpg` | 16:9, 2400×1350, dark with negative space |
| Apply portrait | `editorial.apply` | `media/editorial/apply.jpg` | 4:5, 1200×1500 |
| Client footage | `clientFootage` | `media/clients/client-training-01.mp4` | 4:5, 6–10 s, **client consent** |
| Transformations | `transformations` (list — add more) | `client-01-before.jpg` + `client-01-after.jpg` … | 4:5, 1200×1500, same pose & light, **written consent** — fill in `name` and `caption` |
| Closing film | `closing` | `media/closing/closing-desktop.mp4` + `-mobile.mp4` | 8–12 s loop, no audio, < 8 MB |
| Social share image | — | `media/og/big-grit-og.jpg` | 1200×630 (already made from the authentic photo) |

## Compressing footage (recommended)

Install ffmpeg once (`brew install ffmpeg` on Mac), then from the site folder:

```bash
# Hero clip: makes desktop + vertical mobile + poster. Optional: start second, length
./scripts/optimize-media.sh video ~/Footage/A001.mov media/hero/hero-01 12 7

# Looping section footage
./scripts/optimize-media.sh loop  ~/Footage/B014.mov media/training/training-01 3 8

# Founder interview (keeps audio)
./scripts/optimize-media.sh film  ~/Footage/interview.mov media/interview/founder-film

# Photos (resized to 2000 px, web quality)
./scripts/optimize-media.sh photo ~/Photos/portrait.tif media/founder/delone-portrait
```

The mobile version is a centre crop — for the hero, export a dedicated vertical edit if the action isn't centred.

## Framing tips

* `focus: "60% 30%"` keeps that point of the frame visible when the screen crops it (x% from left, y% from top). Faces near the top → lower y value.
* `fit: "frame"` presents a vertical photo at full height on wide screens over a blurred copy of itself (used for the current hero still).
* `duration` is how long a hero slide holds before the next cut.
* Shoot hero clips with motion *into* the frame and clean negative space on the left (desktop headline side) and lower third (mobile headline side).

## What came from the original file

The original `GritFit_Interactive_Demo.html` contained **one authentic photograph** (Delone, single-arm dumbbell row), embedded twice. It's at `media/founder/delone-dumbbell-row.jpg` (731×1081) and is used as the fallback everywhere. It is too small for crisp full-screen desktop use, which is why the design presents it framed. **Please supply the original high-resolution export.**
