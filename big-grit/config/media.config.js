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
    deloneNeverStop: {
      type: "image",
      src: "media/editorial/delone-never-stop-bw.webp",
      alt: "Delone Carter in a tank top looking into the camera, NEVER STOP sign behind him",
    },
    deloneCurl: {
      type: "image",
      src: "media/details/delone-curl-detail-bw.webp",
      alt: "Close detail of Delone Carter mid-curl",
    },
    deloneCloseup: {
      type: "image",
      src: "media/founder/delone-closeup-bw.webp",
      alt: "Close-up portrait of Delone Carter smiling, cap on backwards",
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
      { src: "media/hero/flash-abs-bw-sm.webp", focus: "50% 30%", zoom: 1 },
      { src: "media/hero/flash-back-bw-sm.webp", focus: "50% 35%", zoom: 1 },
      { src: "media/hero/flash-front-pose-bw-sm.webp", focus: "50% 30%", zoom: 1 },
    ],
  },

  heroSequence: [
    {
      type: "video",
      src: "media/hero/hero-01-desktop.mp4",
      srcMobile: "media/hero/hero-01-mobile.mp4",
      poster: "media/hero/hero-01-poster.jpg",
      caption: "Training — Dallas",
      alt: "Delone Carter on a machine press",
      duration: 6.5,
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
      src: "media/hero/delone-bench-row-wide-bw.webp",
      caption: "The work",
      alt: "Delone Carter rowing a dumbbell over a bench in a crowded gym",
      duration: 4.5,
      fit: "cover",
      focus: "55% 40%",
      motion: "drift",
    },
    {
      type: "video",
      src: "media/hero/hero-02-desktop.mp4",
      srcMobile: "media/hero/hero-02-mobile.mp4",
      poster: "media/hero/hero-02-poster.jpg",
      caption: "The work",
      alt: "Delone Carter doing walking lunges with a medicine ball",
      duration: 6.5,
      focus: "50% 50%",
      spec: "Desktop 1920×1080 + Mobile 1080×1920 · MP4 (H.264) · 4–7 s · no audio · under 6 MB",
    },
    {
      type: "image",
      src: "media/hero/delone-neon-seated-wide-bw.webp",
      caption: "Every rep, earned",
      alt: "Delone Carter seated under neon light lines between sets",
      duration: 4.5,
      fit: "cover",
      focus: "50% 45%",
      motion: "push",
    },
    {
      type: "image",
      src: "media/hero/delone-sled-push.webp",
      caption: "Dallas–Fort Worth",
      alt: "Delone Carter driving a weighted sled toward the camera",
      duration: 4.5,
      fit: "frame",
      focus: "50% 40%",
      motion: "push",
    },
    {
      type: "video",
      src: "media/hero/hero-03-desktop.mp4",
      srcMobile: "media/hero/hero-03-mobile.mp4",
      poster: "media/hero/hero-03-poster.jpg",
      caption: "Conditioning",
      alt: "Delone Carter on a ski erg, seen from behind",
      duration: 6.5,
      focus: "50% 40%",
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
      alt: "Delone Carter training on a machine row",
      focus: "50% 30%",
      fallback: "deloneNeverStop",
      spec: "Training footage · 1600×2000 (4:5 vertical) MP4 · 6–10 s seamless loop · under 5 MB",
    },
    detail: {
      type: "video",
      src: "",   // add when ready: "media/details/detail-01.mp4"
      poster: "media/details/detail-01-poster.jpg",
      alt: "Slow-motion detail: chalked hands gripping a barbell",
      focus: "50% 45%",
      fallback: "deloneCurl",
      spec: "Cinematic detail (chalk, grip, breath, sweat) · 1440×1080 MP4 · 4–6 s loop · under 3 MB",
    },
  },

  /* Full-width cinematic band between sections */
  detailBand: {
    type: "video",
    src: "media/details/detail-02.mp4",
    srcMobile: "media/details/detail-02-mobile.mp4",
    poster: "media/details/detail-02-poster.jpg",
    alt: "Slow close-up of a 45 lb plate on a loaded bar",
    focus: "50% 50%",
    fallback: "deloneLatPulldown",
    spec: "Wide cinematic detail · 2560×1080 (or 1920×1080) MP4 · 6–10 s loop · under 6 MB · Mobile 1080×1350",
  },

  /* ------------------------------------------------------------------------
     03 · MEET DELONE — portraits per chapter + founder film
     ------------------------------------------------------------------------ */
  founder: {
    portrait: {
      type: "image",
      src: "media/founder/delone-portrait-jacket.webp",
      alt: "Portrait of Delone Carter in a hooded jacket, looking into the camera",
      focus: "50% 22%",
      fallback: "deloneRow",
      spec: "Founder portrait · 1600×2000 (4:5)",
    },
    chapters: [
      {
        type: "image",
        src: "",   // add when ready: "media/founder/chapter-01-the-field.jpg"
        alt: "Delone Carter during his football career",
        focus: "50% 40%",
        spec: "Syracuse / Colts-era photo · 1600×2000 JPG · confirm you hold usage rights (team and agency photos are usually licensed)",
      },
      {
        type: "image",
        src: "media/founder/delone-reflect-bw.webp",
        alt: "Delone Carter from behind, hand on his head, in a dark gym",
        focus: "50% 30%",
      },
      {
        type: "image",
        src: "media/founder/delone-front-pose-bw.webp",
        alt: "Delone Carter in a front physique pose",
        focus: "50% 25%",
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
    src: "",   // add when ready: "media/interview/founder-film.mp4" or a YouTube link
    poster: "media/interview/founder-film-poster.jpg",
    title: "In his words",
    alt: "Founder film: Delone Carter on football, the hip replacement and rebuilding",
    fallbackPoster: "deloneCloseup",
    spec: "Founder interview · 1920×1080 MP4 (H.264 + AAC audio) · 60–180 s · under 40 MB — or a YouTube/Vimeo link",
  },

  /* Career reel ("The Tape") in Meet Delone — an auto-playing highlight reel.
     It starts muted when scrolled into view and moves through the chapters
     on its own (sound and pause buttons on the player).
     YouTube chapters play through YouTube's own player — nothing is
     downloaded or re-hosted. Set the best moment of each with:
       start: second to begin at      end: second to stop at
     (no "end" = start + clipSeconds). Chapters with "video" play a file
     from /media instead. "link" adds a "watch the full thing" link. */
  careerReel: {
    clipSeconds: 20,
    chapters: [
      { era: "High school", title: "Where it started", url: "https://youtu.be/GwFntQnWeEQ", start: 0, credit: "Video: anthonyqb2 on YouTube" },
      { era: "Syracuse", title: "Big East Offensive Player of the Week", url: "https://youtu.be/UYWrfTqKgcQ", start: 0, credit: "Video: Scotsman Media Group on YouTube" },
      { era: "Indianapolis Colts", title: "Touchdown vs. Titans · 12/9/12", url: "https://youtu.be/ZY6SEeFyY_o", start: 0, credit: "Video: K Michael McCammack on YouTube" },
      { era: "IFBB Pro", title: "Still building", video: "media/reel/today.mp4", poster: "media/reel/today-poster.jpg", credit: "Club Studio footage",
        link: "https://www.instagram.com/reel/DJwmR0Ht1u4/", linkLabel: "Watch the stage reel on Instagram" },
    ],
  },

  /* ------------------------------------------------------------------------
     04 · COACHING — imagery shown with each coaching option
     ------------------------------------------------------------------------ */
  coaching: {
    inPerson: {
      type: "image",
      src: "media/editorial/coaching-in-person-smile.webp",
      alt: "Delone Carter smiling in the gym",
      focus: "55% 35%",
      fallback: "deloneRow",
    },
    online: {
      type: "image",
      src: "media/editorial/coaching-online-tank.webp",
      alt: "Delone Carter standing in a tank top in a training studio",
      focus: "50% 25%",
      fallback: "deloneRow",
    },
    consult: {
      type: "image",
      src: "media/details/delone-back-crowd-bw.webp",
      alt: "Delone Carter from behind in a busy gym",
      focus: "50% 35%",
      fallback: "deloneRow",
    },
  },

  /* ------------------------------------------------------------------------
     05 · CLIENT RESULTS — real clients only, with written permission
     ------------------------------------------------------------------------ */
  clientFootage: {
    type: "video",
    src: "",   // add when ready: "media/clients/client-training-01.mp4"
    poster: "media/clients/client-training-01-poster.jpg",
    alt: "A Big Grit client training with Delone",
    focus: "50% 50%",
    spec: "Client training footage · 1600×2000 (4:5) MP4 · 6–10 s loop · under 5 MB · client permission required",
  },

  /* Full-bleed interludes that give the page its rhythm */
  interludes: {
    photo: {
      type: "image",
      src: "media/founder/delone-closeup-bw.webp",
      alt: "Close-up portrait of Delone Carter smiling, cap on backwards",
      focus: "55% 32%",
      fallback: "deloneRow",
    },
    video: {
      type: "video",
      src: "media/interludes/the-work-desktop.mp4",
      srcMobile: "media/interludes/the-work-mobile.mp4",
      poster: "media/interludes/the-work-poster.jpg",
      alt: "Delone Carter doing walking lunges down the turf of an empty gym",
      focus: "50% 50%",
      fallback: "deloneLatPulldown",
      spec: "Wide cinematic shot with negative space · 1920×1080 + 1080×1920 MP4 · 6–10 s loop · no audio",
    },
  },

  /* Feature photo beside the results heading */
  resultFeature: {
    type: "image",
    src: "media/clients/delone-with-client.webp",
    alt: "Delone Carter and a client flexing together in the gym",
    caption: "Delone with a client",
  },

  /* Each transformation becomes an editorial case study. Use EITHER
       image: one photo that already shows before and after
              (add sideBySide: true to print "Before / After" on it)
     OR
       before + after: two separate photos (adds a drag-to-compare slider).
     Only fill in what is VERIFIED with the client — empty fields are hidden:
       client     how they want to be named, e.g. "Marcus T." (or leave empty)
       headline   one factual line, e.g. "Down two belt sizes in 12 weeks"
       program    "Online coaching" / "Private training · DFW"
       timeframe  e.g. "12 weeks" or "October – December 2020"
       focus      e.g. "Fat loss" / "Recomposition"
       quote      their words, exactly as they said them
     Real clients with written permission only. See docs/CASE-STUDIES.md. */
  transformations: [
    {
      image: "media/transformations/client-01.webp",
      sideBySide: true,
      alt: "Client before and after, side by side, with a visibly smaller waist",
      client: "", headline: "", program: "", timeframe: "", focus: "", quote: "",
    },
    {
      image: "media/transformations/client-02.webp",
      sideBySide: true,
      alt: "Client before and after in profile, with a visibly flatter midsection",
      client: "", headline: "", program: "", timeframe: "", focus: "", quote: "",
    },
    {
      image: "media/transformations/client-03.webp",
      alt: "Client before and after: October 2020 and December 2020, noticeably leaner",
      client: "", headline: "", program: "", timeframe: "October – December 2020", focus: "", quote: "",
    },
  ],

  /* ------------------------------------------------------------------------
     06 · EDITORIAL — assessment & application backdrops
     ------------------------------------------------------------------------ */
  editorial: {
    assessment: {
      type: "image",
      src: "media/editorial/find-your-grit-neon-bw.webp",
      alt: "",
      focus: "50% 40%",
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
    alt: "Close-up of Delone Carter between sets, then laughing",
    focus: "60% 35%",
    fallback: "deloneGritTank",
    spec: "Closing campaign film · Desktop 1920×1080 + Mobile 1080×1920 MP4 · 8–12 s loop · no audio · under 8 MB",
  },
};
