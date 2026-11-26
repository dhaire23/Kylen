/* ==========================================================================
   BIG GRIT PERFORMANCE — MEDIA LIBRARY
   --------------------------------------------------------------------------
   Every photo and video on the website is listed here.

   THE EASY WAY TO REPLACE MEDIA (no editing required):
     1. Export your file to the size/format listed in its "spec".
     2. Name it EXACTLY as shown in "src" (e.g. hero-01-desktop.mp4).
     3. Drop it into that folder inside /media and re-deploy.
   The site detects the new file automatically. Missing files are skipped
   gracefully — nothing breaks. Video slots fall back to the photo named
   in "fallback" until you add footage.

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
     fallback    name of a photo in "library" to show until the file exists
   ========================================================================== */

window.BIG_GRIT_MEDIA = {

  /* While building the site, show labelled slots for media that has not
     been supplied yet. SET TO false BEFORE LAUNCH. */
  showPlaceholders: false,

  /* Photos used as stand-ins while dedicated footage is still missing. */
  library: {
    deloneRow: {
      type: "image",
      src: "media/founder/delone-row-color.webp",
      alt: "Delone Carter mid-set on a single-arm dumbbell row in a dark gym",
      width: 1200, height: 1800,
    },
    delonePortrait: {
      type: "image",
      src: "media/founder/delone-portrait.webp",
      alt: "Delone Carter standing relaxed, shirtless, in a gym",
    },
    deloneBackGym: {
      type: "image",
      src: "media/editorial/delone-back-gym.webp",
      alt: "Delone Carter from behind in the gym, cap on, lights blurred",
    },
    deloneLatPulldown: {
      type: "image",
      src: "media/details/delone-lat-pulldown.webp",
      alt: "Delone Carter from behind at the top of a lat pulldown",
    },
    deloneGritTank: {
      type: "image",
      src: "media/closing/delone-grit-tank.webp",
      alt: "Delone Carter smiling in sunglasses and a GRIT tank, arms open",
    },
  },

  /* ------------------------------------------------------------------------
     01 · OPENING SEQUENCE (homepage hero)
     Plays top to bottom, then loops. Add, remove or reorder freely.
     Videos: muted, no audio track needed. Keep each under ~6 MB.
     ------------------------------------------------------------------------ */
  heroPoster: "media/founder/delone-row-color.webp",

  /* Entrance "flash cut": up to 4 stills punched in one after another inside a
     portrait window, before the opening sequence takes over full screen. */
  heroIntro: {
    stills: [
      { src: "media/founder/delone-row-bw.webp", focus: "50% 30%", zoom: 1 },
      { src: "media/founder/delone-back-pose.webp", focus: "50% 30%", zoom: 1 },
      { src: "media/founder/delone-portrait.webp", focus: "50% 25%", zoom: 1 },
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
      src: "media/founder/delone-row-color.webp",
      caption: "Delone Carter — IFBB Pro",
      alt: "Delone Carter training",
      duration: 5,
      fit: "frame",
      focus: "55% 35%",
      motion: "push",
    },
    {
      type: "image",
      src: "media/hero/delone-seated-machine.webp",
      caption: "The work",
      alt: "Delone Carter seated on a leg press machine in a dark gym",
      duration: 4.5,
      fit: "cover",
      focus: "50% 35%",
      motion: "drift",
    },
    {
      type: "video",
      src: "media/hero/hero-02-desktop.mp4",
      srcMobile: "media/hero/hero-02-mobile.mp4",
      poster: "media/hero/hero-02-poster.jpg",
      caption: "Every rep, earned",
      duration: 6,
      focus: "50% 50%",
      spec: "Desktop 1920×1080 + Mobile 1080×1920 · MP4 (H.264) · 4–7 s · no audio · under 6 MB",
    },
    {
      type: "image",
      src: "media/hero/delone-abs-cap.webp",
      caption: "Every rep, earned",
      alt: "Delone Carter pulling his cap down over his face, abs flexed",
      duration: 4.5,
      fit: "cover",
      focus: "50% 30%",
      motion: "push",
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
      focus: "50% 30%",
      fallback: "deloneBackGym",
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
    alt: "Delone Carter from behind at the top of a lat pulldown",
    focus: "50% 35%",
    fallback: "deloneLatPulldown",
    spec: "Wide cinematic detail · 2560×1080 (or 1920×1080) MP4 · 6–10 s loop · under 6 MB · Mobile 1080×1350",
  },

  /* ------------------------------------------------------------------------
     03 · MEET DELONE — portraits per chapter + founder film
     ------------------------------------------------------------------------ */
  founder: {
    portrait: {
      type: "image",
      src: "media/founder/delone-portrait.webp",
      alt: "Portrait of Delone Carter, founder of Big Grit Performance",
      focus: "50% 25%",
      fallback: "deloneRow",
      spec: "Founder portrait · 1600×2000 (4:5)",
    },
    chapters: [
      {
        type: "image",
        src: "media/founder/chapter-01-the-field.jpg",
        alt: "Delone Carter during his football career",
        focus: "50% 40%",
        spec: "Syracuse / Colts-era photo · 1600×2000 JPG · confirm you hold usage rights (team and agency photos are usually licensed)",
      },
      {
        type: "image",
        src: "media/founder/delone-back-pose.webp",
        alt: "Delone Carter from behind, hands on his head",
        focus: "50% 30%",
      },
      {
        type: "image",
        src: "media/founder/delone-row-bw.webp",
        alt: "Delone Carter mid-set on a dumbbell row, looking into the camera",
        focus: "50% 30%",
        fallback: "deloneRow",
      },
      {
        type: "image",
        src: "media/clients/delone-with-client.webp",
        alt: "Delone Carter and a client flexing together in the gym",
        focus: "50% 40%",
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
    alt: "Founder film: Delone Carter on football, the hip replacement and rebuilding",
    fallbackPoster: "deloneRow",
    spec: "Founder interview · 1920×1080 MP4 (H.264 + AAC audio) · 60–180 s · under 40 MB — or a YouTube/Vimeo link",
  },

  /* ------------------------------------------------------------------------
     04 · COACHING — imagery shown with each coaching option
     ------------------------------------------------------------------------ */
  coaching: {
    inPerson: {
      type: "image",
      src: "media/editorial/coaching-in-person.webp",
      alt: "Delone Carter laughing in the gym",
      focus: "60% 35%",
      fallback: "deloneRow",
    },
    online: {
      type: "image",
      src: "media/editorial/coaching-online.webp",
      alt: "Delone Carter in profile, towel in hand",
      focus: "50% 22%",
      fallback: "deloneRow",
    },
    consult: {
      type: "image",
      src: "media/editorial/delone-back-gym.webp",
      alt: "Delone Carter from behind in the gym",
      focus: "50% 35%",
      fallback: "deloneRow",
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

  /* Feature photo shown first in the results row */
  resultFeature: {
    type: "image",
    src: "media/clients/delone-with-client.webp",
    alt: "Delone Carter and a client flexing together in the gym",
    caption: "Delone with a client",
  },

  /* Each transformation is EITHER
       image: one photo that already shows before and after
              (add sideBySide: true to print "Before / After" labels on it)
     OR
       before + after: two separate photos of the same person (adds a drag slider)
     Add as many as you like. Real clients with written permission only. */
  transformations: [
    {
      image: "media/transformations/client-01.webp",
      sideBySide: true,
      alt: "Client before and after, side by side, with a visibly smaller waist",
      name: "",
      caption: "",
    },
    {
      image: "media/transformations/client-02.webp",
      sideBySide: true,
      alt: "Client before and after in profile, with a visibly flatter midsection",
      name: "",
      caption: "",
    },
    {
      image: "media/transformations/client-03.webp",
      alt: "Client before and after: October 2020 and December 2020, noticeably leaner",
      name: "",
      caption: "October to December 2020",
    },
  ],

  /* ------------------------------------------------------------------------
     06 · EDITORIAL — assessment & application backdrops
     ------------------------------------------------------------------------ */
  editorial: {
    assessment: {
      type: "image",
      src: "media/editorial/find-your-grit.webp",
      alt: "",
      focus: "50% 35%",
      fallback: "deloneRow",
    },
    apply: {
      type: "image",
      src: "media/editorial/apply.webp",
      alt: "Delone Carter laughing, lifting his tank",
      focus: "55% 30%",
      fallback: "deloneRow",
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
    alt: "Delone Carter smiling in a GRIT tank, arms open",
    focus: "60% 30%",
    fallback: "deloneGritTank",
    spec: "Closing campaign film · Desktop 1920×1080 + Mobile 1080×1920 MP4 · 8–12 s loop · no audio · under 8 MB",
  },
};
