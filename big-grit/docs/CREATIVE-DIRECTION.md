# Creative direction

## Idea: *Built the hard way.*

Big Grit isn't sold as a gym service; it's presented as a campaign about a rebuilt athlete. The site moves like a sports documentary: a cold open (opening sequence), a thesis (philosophy), a protagonist (Delone's four chapters), a choice (coaching), a personal test (Find Your Grit), proof (results), a commitment (apply) and a closing title card ("Nobody gives you grit.").

## References — what was and wasn't possible

The brief named THE GRIND (primary), Corey Calliet, ACHV Fitness and Essor Fitness.

* **THE GRIND (thegrind.nl)** — after the environment was opened to `www.thegrind.nl`, its **HTML, inline styles and inline scripts** were studied. Its CDN, video host and animation-script hosts stayed blocked, so the page was never seen *playing*: exact timings and easing in their external animation file are unknown. What the markup shows, and what Big Grit adapted (in its own code, type and colour — no assets or code reused):
  * **Hero entrance:** two photographs and a looping video stacked in one frame, each masked shut from the bottom and revealed in turn; edge overlays that frame the media before it opens; an SVG logo drawn as a stroke → Big Grit's stroke-drawn wordmark, portrait "window" with flash-cut stills that lands on the live sequence and expands to full bleed.
  * **Assessment step 1 inside the hero** ("Stap 1/4" + select + Next) → Big Grit's hero quick-start: answer question 1 there, land on question 2.
  * **Founder video thumbnail in the hero** → the "Delone's story" film shortcut.
  * **Split reveals by lines, words and characters; scroll-scrubbed text highlight; parallax with start/end values; a marquee that follows scroll direction and speed** → letter reveals on "Delone Carter" and "grit.", the existing line/word reveals, and a marquee that now reverses when you scroll up.
  * Built on GSAP + ScrollTrigger + SplitText with Locomotive smooth scroll; Big Grit uses GSAP + ScrollTrigger with Lenis (touch devices keep native scrolling).
* **Corey Calliet, ACHV, Essor** — not reachable from the build environment; not inspected. Their influence follows the brief's description only (founder authority and conversion; editorial restraint and spacing).

## Design system

| Token | Value | Use |
|---|---|---|
| Ink | `#070908` | page — near-black with a green undertone from the shoot |
| Bone | `#ECE8DE` | type — warm, filmic off-white instead of pure white |
| Grit | `#C6EF78` | the original GritFit lime — restrained: CTAs, numbers, one word per headline |
| Forest | `#12261F` | the cap/joggers green from the authentic photo; assessment backdrop |

**Type:** Archivo variable at 62% width / weight 860 for campaign headlines (condensed, uppercase, tight .84 leading) set against Instrument Serif *italic* for the human voice ("the hard", "grit.", "real work."). Body in Archivo at normal width. Labels are tracked micro-caps with lime numerals — a numbered, chaptered editorial rhythm.

**Composition:** full-bleed media, hairline rules instead of cards, generous negative space, asymmetric splits (5/7), numbered rows rather than grids of boxes. No gradients-as-decoration, no glass, no rounded cards.

**Texture:** a subtle animated film-grain layer over everything; footage is shaded with directional gradients so type always sits on clean darkness.

## Motion system

| Moment | Behaviour |
|---|---|
| Brand reveal | "BIG GRIT" draws itself in outline, fills, and the curtain lifts. ≈1.5 s, once per visit, never blocks interaction |
| Entrance | The hero opens as a portrait window; stills from `heroIntro` cut in one after another, then the window expands to full screen onto the live sequence (every visit, ≈2 s) |
| Hero quick-start | Desktop: Find Your Grit question 1 in the hero; Next lands on question 2 |
| Opening sequence | Footage and stills cut with a bottom-up masked wipe; stills get a slow camera push or drift; progress bar + caption + counter like a broadcast lower third |
| Headlines | Lines rise from behind a mask (expo-out) as they enter |
| Images | Reveal through a vertical mask while scaling from 1.3 → 1 |
| Philosophy | Statement words light up as you read (scroll-scrubbed) |
| Manifesto band | Kinetic marquee that speeds up with scroll velocity and reverses when you scroll up |
| Founder name, closing line | Letter-by-letter reveal |
| "No shortcuts." | The two words slide past each other over the footage |
| Founder chapters | Sticky frame; each chapter wipes a new photograph over the last |
| Coaching | Desktop: an image follows the cursor across each row, tilting with speed |
| Assessment | Answers stagger in; selection auto-advances |
| Scrolling | Lenis smooth scroll on mouse/trackpad only; native scrolling on touch; no scroll-jacking or pinned hijacks |

Under the "reduce motion" setting (iOS/macOS, Android "Remove animations") the page drops every zoom, wipe, parallax and sliding-text effect, but the opening still plays its muted footage and crossfades between slides, and section videos still play; the pause button stops all of it. Without JavaScript the page is static and fully readable.

## Shot list for your next session (to fill the slots)

1. **Hero 01** — wide, slow push toward Delone mid-set, haze/back-light; leave left third empty.
2. **Hero 02** — macro: chalk off hands, knurling, breath, sweat (1–2 s each).
3. **Hero 03** — Delone walking into frame toward camera, ends on a held look.
4. **Training loop** — Delone coaching a client, vertical 4:5.
5. **Detail band** — ultra-slow-motion single action (plate hitting floor, rope slam) for 21:9.
6. **Founder portraits** — one still, unsmiling, direct to lens; one in profile.
7. **Interview** — the football → setback → rebuild story in his words, 2–3 minutes.
8. **Closing** — silhouette against a bright doorway or sunrise; end on stillness.
