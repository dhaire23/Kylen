/* ==========================================================================
   BIG GRIT PERFORMANCE — MEDIA LIBRARY
   --------------------------------------------------------------------------
   Every photo and video on the website is listed here.

   THE EASY WAY TO REPLACE MEDIA (no editing required):
     1. Export your file to the size/format listed in its "spec".
     2. Name it EXACTLY as shown in "src" (e.g. hero-01-desktop.mp4).
     3. Drop it into that folder inside /media and re-deploy.
   The site detects the new file automatically. Missing files are skipped
   gracefully — nothing breaks.

   THE FLEXIBLE WAY:
     Change a "src" to any other file name or full URL (e.g. a CDN link).

   Field guide:
     type        "video" or "image"
     src         desktop / default file
     srcMobile   optional vertical (9:16 or 4:5) version for phones
     poster      still frame shown while a video loads (JPG/WebP)
     alt         describe the image for screen readers & Google
     caption     small label shown on screen (hero only)
     duration    seconds a slide stays on screen (hero only; videos play
                 their own length up to this many seconds)
     focus       which part of the frame must stay visible when cropped,
                 as "x% y%" — "50% 50%" is centre, "70% 20%" is upper right
     zoom        1 = no zoom. 1.4 = tighter crop (use for detail shots)
     zoomMobile  optional gentler zoom for phones (default: max 1.2)
     fit         "cover"  fills the screen
                 "frame"  shows a portrait photo sharp at full height on
                          wide screens over a dark, blurred version of itself
                          (best for vertical photos on desktop)
   ========================================================================== */

window.BIG_GRIT_MEDIA = {

  /* While building the site, show labelled slots for media that has not
     been supplied yet. SET TO false BEFORE LAUNCH. */
  showPlaceholders: true,

  /* The one authentic photograph found in the original site file.
     Used as the fallback wherever a dedicated asset is still missing. */
  library: {
    deloneRow: {
      type: "image",
      src: "media/founder/delone-dumbbell-row.jpg",
      webp: "media/founder/delone-dumbbell-row.webp",
      alt: "Delone Carter performing a single-arm dumbbell row on an incline bench in a dark industrial gym",
      width: 731, height: 1081,
    },
  },

  /* ------------------------------------------------------------------------
     01 · OPENING SEQUENCE (homepage hero)
     Plays top to bottom, then loops. Add, remove or reorder freely.
     Videos: muted, no audio track needed. Keep each under ~6 MB.
     ------------------------------------------------------------------------ */
  heroPoster: "media/founder/delone-dumbbell-row.jpg",

  /* Entrance "flash cut": up to 4 stills punched in one after another inside a
     portrait window, before the opening sequence takes over full screen.
     Best with 2–3 strong, different photos (1600×2000 or larger).
     zoom > 1 makes a tighter, detail crop of the same photo. */
  heroIntro: {
    stills: [
      { src: "media/founder/delone-dumbbell-row.webp", focus: "55% 30%", zoom: 1 },
      { src: "media/founder/delone-dumbbell-row.webp", focus: "38% 40%", zoom: 1.6 },
    ],
  },

  heroSequence: [
    {
      type: "video",
      src: "media/hero/hero-01-desktop.mp4",
      srcMobile: "media/hero/hero-01-mobile.mp4",
      poster: "media/hero/hero-01-poster.jpg",
      caption: "Training — Dallas",
      duration: 7,
      focus: "50% 40%",
      spec: "Desktop 1920×1080 + Mobile 1080×1920 · MP4 (H.264) · 5–8 s · no audio · under 6 MB",
    },
    {
      type: "image",
      src: "media/founder/delone-dumbbell-row.jpg",
      caption: "Delone Carter — IFBB Pro",
      alt: "Delone Carter training",
      duration: 5,
      fit: "frame",
      focus: "55% 35%",
      motion: "push",
    },
    {
      type: "video",
      src: "media/hero/hero-02-desktop.mp4",
      srcMobile: "media/hero/hero-02-mobile.mp4",
      poster: "media/hero/hero-02-poster.jpg",
      caption: "The work",
      duration: 6,
      focus: "50% 50%",
      spec: "Desktop 1920×1080 + Mobile 1080×1920 · MP4 (H.264) · 4–7 s · no audio · under 6 MB",
    },
    {
      type: "image",
      src: "media/founder/delone-dumbbell-row.jpg",
      caption: "Every rep, earned",
      alt: "Close detail of Delone Carter's arm and shoulder during a dumbbell row",
      duration: 4.5,
      fit: "frame",
      zoom: 1.4,
      zoomMobile: 1.2,
      focus: "38% 42%",
      motion: "drift",
    },
    {
      type: "video",
      src: "media/hero/hero-03-desktop.mp4",
      srcMobile: "media/hero/hero-03-mobile.mp4",
      poster: "media/hero/hero-03-poster.jpg",
      caption: "Dallas–Fort Worth",
      duration: 6,
      focus: "50% 50%",
      spec: "Desktop 1920×1080 + Mobile 1080×1920 · MP4 (H.264) · 4–7 s · no audio · under 6 MB",
    },
  ],

  /* ------------------------------------------------------------------------
     02 · PHILOSOPHY — secondary training video + cinematic detail
     ------------------------------------------------------------------------ */
  philosophy: {
    training: {
      type: "video",
      src: "media/training/training-01.mp4",
      poster: "media/training/training-01-poster.jpg",
      alt: "Delone coaching a training session",
      focus: "50% 50%",
      fallback: "deloneRow",
      spec: "Training footage · 1600×2000 (4:5 vertical) MP4 · 6–10 s seamless loop · under 5 MB",
    },
    detail: {
      type: "video",
      src: "media/details/detail-01.mp4",
      poster: "media/details/detail-01-poster.jpg",
      alt: "Slow-motion detail: chalked hands gripping a barbell",
      focus: "50% 50%",
      spec: "Cinematic detail (chalk, grip, breath, sweat) · 1440×1080 MP4 · 4–6 s loop · under 3 MB",
    },
  },

  /* Full-width cinematic band between sections */
  detailBand: {
    type: "video",
    src: "media/details/detail-02.mp4",
    srcMobile: "media/details/detail-02-mobile.mp4",
    poster: "media/details/detail-02-poster.jpg",
    alt: "Cinematic slow-motion training detail",
    focus: "50% 50%",
    fallback: "deloneRow",
    spec: "Wide cinematic detail · 2560×1080 (or 1920×1080) MP4 · 6–10 s loop · under 6 MB · Mobile 1080×1350",
  },

  /* ------------------------------------------------------------------------
     03 · MEET DELONE — portraits per chapter + founder film
     ------------------------------------------------------------------------ */
  founder: {
    portrait: {
      type: "image",
      src: "media/founder/delone-portrait.jpg",
      alt: "Portrait of Delone Carter, founder of Big Grit Performance",
      focus: "50% 30%",
      fallback: "deloneRow",
      spec: "Founder portrait · 1600×2000 (4:5) JPG · under 500 KB",
    },
    chapters: [
      {
        type: "image",
        src: "media/founder/chapter-01-the-field.jpg",
        alt: "Delone Carter during his NFL career",
        focus: "50% 40%",
        spec: "NFL-era photo · 1600×2000 JPG · ensure you hold usage rights (team/agency photos are often licensed)",
      },
      {
        type: "image",
        src: "media/founder/chapter-02-the-setback.jpg",
        alt: "Delone Carter in a reflective moment",
        focus: "50% 40%",
        spec: "Moody, quiet portrait or recovery moment · 1600×2000 JPG",
      },
      {
        type: "image",
        src: "media/founder/chapter-03-the-rebuild.jpg",
        alt: "Delone Carter training during his rebuild",
        focus: "55% 35%",
        fallback: "deloneRow",
        spec: "Training / stage / IFBB photo · 1600×2000 JPG",
      },
      {
        type: "image",
        src: "media/founder/chapter-04-the-coach.jpg",
        alt: "Delone Carter coaching a client",
        focus: "50% 40%",
        spec: "Delone coaching a real client (with their permission) · 1600×2000 JPG",
      },
    ],
  },

  /* Founder interview film. Use an MP4 file OR paste a YouTube / Vimeo link
     into "src" (e.g. "https://www.youtube.com/watch?v=XXXX"). */
  interview: {
    type: "video",
    src: "media/interview/founder-film.mp4",
    poster: "media/interview/founder-film-poster.jpg",
    title: "In his words",
    alt: "Founder film: Delone Carter on football, adversity and rebuilding",
    fallbackPoster: "deloneRow",
    spec: "Founder interview · 1920×1080 MP4 (H.264 + AAC audio) · 60–180 s · under 40 MB — or a YouTube/Vimeo link",
  },

  /* ------------------------------------------------------------------------
     04 · COACHING — imagery shown with each coaching option
     ------------------------------------------------------------------------ */
  coaching: {
    inPerson: {
      type: "image",
      src: "media/editorial/coaching-in-person.jpg",
      alt: "Delone coaching a client in person in Dallas–Fort Worth",
      focus: "50% 40%",
      fallback: "deloneRow",
      spec: "Editorial photo · 1200×1500 JPG",
    },
    online: {
      type: "image",
      src: "media/editorial/coaching-online.jpg",
      alt: "Online coaching with Big Grit Performance",
      focus: "50% 40%",
      fallback: "deloneRow",
      spec: "Editorial photo · 1200×1500 JPG (e.g. client training solo, phone check-in)",
    },
    consult: {
      type: "image",
      src: "media/editorial/coaching-consult.jpg",
      alt: "Delone Carter",
      focus: "50% 40%",
      fallback: "deloneRow",
      spec: "Editorial photo · 1200×1500 JPG",
    },
  },

  /* ------------------------------------------------------------------------
     05 · CLIENT RESULTS — real clients only, with written permission
     ------------------------------------------------------------------------ */
  clientFootage: {
    type: "video",
    src: "media/clients/client-training-01.mp4",
    poster: "media/clients/client-training-01-poster.jpg",
    alt: "A Big Grit client training with Delone",
    focus: "50% 50%",
    spec: "Client training footage · 1600×2000 (4:5) MP4 · 6–10 s loop · under 5 MB · client permission required",
  },

  /* Each transformation: before + after photo of the SAME real client.
     Add as many as you like. Leave the list empty to hide the section
     (once showPlaceholders is false). */
  transformations: [
    {
      before: "media/transformations/client-01-before.jpg",
      after: "media/transformations/client-01-after.jpg",
      name: "",          // e.g. "Marcus T."
      caption: "",       // e.g. "In-person · 20 weeks"
      spec: "Before + after · 1200×1500 (4:5) JPG each · same pose & lighting · written client consent",
    },
    {
      before: "media/transformations/client-02-before.jpg",
      after: "media/transformations/client-02-after.jpg",
      name: "",
      caption: "",
      spec: "Before + after · 1200×1500 (4:5) JPG each · same pose & lighting · written client consent",
    },
    {
      before: "media/transformations/client-03-before.jpg",
      after: "media/transformations/client-03-after.jpg",
      name: "",
      caption: "",
      spec: "Before + after · 1200×1500 (4:5) JPG each · same pose & lighting · written client consent",
    },
  ],

  /* ------------------------------------------------------------------------
     06 · EDITORIAL — assessment & application backdrops
     ------------------------------------------------------------------------ */
  editorial: {
    assessment: {
      type: "image",
      src: "media/editorial/find-your-grit.jpg",
      alt: "",
      focus: "60% 40%",
      fallback: "deloneRow",
      spec: "Atmospheric wide photo · 2400×1350 JPG · dark, with negative space",
    },
    apply: {
      type: "image",
      src: "media/editorial/apply.jpg",
      alt: "Delone Carter",
      focus: "50% 35%",
      fallback: "deloneRow",
      spec: "Founder editorial portrait · 1200×1500 JPG",
    },
  },

  /* ------------------------------------------------------------------------
     07 · CLOSING CAMPAIGN FILM
     ------------------------------------------------------------------------ */
  closing: {
    type: "video",
    src: "media/closing/closing-desktop.mp4",
    srcMobile: "media/closing/closing-mobile.mp4",
    poster: "media/closing/closing-poster.jpg",
    alt: "Closing campaign film",
    focus: "50% 40%",
    fallback: "deloneRow",
    spec: "Closing campaign film · Desktop 1920×1080 + Mobile 1080×1920 MP4 · 8–12 s loop · no audio · under 8 MB",
  },
};
