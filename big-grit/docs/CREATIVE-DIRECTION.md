# Creative direction

## Idea: *Built the hard way.*

Big Grit isn't sold as a gym service; it's presented as a campaign about a rebuilt athlete. The site moves like a sports documentary: a cold open (opening sequence), a thesis (philosophy), a protagonist (Delone's four chapters), a choice (coaching), a personal test (Find Your Grit), proof (results), a commitment (apply) and a closing title card ("Nobody gives you grit.").

## References — what was and wasn't possible

The brief named THE GRIND (primary), Corey Calliet, ACHV Fitness and Essor Fitness. **None of these sites could be reached from the build environment** (the network blocked all four domains), so their live pages, footage and animations were *not* inspected. The direction below is built from the qualities the brief describes — immediate full-screen motion, footage/stills interplay, oversized type, athletic campaign rhythm (THE GRIND); founder authority and conversion (Calliet/ACHV); editorial restraint and spacing (Essor) — interpreted into an original system. No assets or code from those sites were used.

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
| Brand reveal | "BIG GRIT" rises, then the screen splits open onto the already-playing hero. ≈1.3 s, once per visit, never blocks interaction |
| Opening sequence | Footage and stills cut with a bottom-up masked wipe; stills get a slow camera push or drift; progress bar + caption + counter like a broadcast lower third |
| Headlines | Lines rise from behind a mask (expo-out) as they enter |
| Images | Reveal through a vertical mask while scaling from 1.3 → 1 |
| Philosophy | Statement words light up as you read (scroll-scrubbed) |
| Manifesto band | Kinetic marquee that speeds up with scroll velocity |
| "No shortcuts." | The two words slide past each other over the footage |
| Founder chapters | Sticky frame; each chapter wipes a new photograph over the last |
| Coaching | Desktop: an image follows the cursor across each row, tilting with speed |
| Assessment | Answers stagger in; selection auto-advances |
| Scrolling | Lenis smooth scroll on mouse/trackpad only; native scrolling on touch; no scroll-jacking or pinned hijacks |

Everything collapses to a static, fully readable page under `prefers-reduced-motion` or without JavaScript, and the hero has a pause control.

## Shot list for your next session (to fill the slots)

1. **Hero 01** — wide, slow push toward Delone mid-set, haze/back-light; leave left third empty.
2. **Hero 02** — macro: chalk off hands, knurling, breath, sweat (1–2 s each).
3. **Hero 03** — Delone walking into frame toward camera, ends on a held look.
4. **Training loop** — Delone coaching a client, vertical 4:5.
5. **Detail band** — ultra-slow-motion single action (plate hitting floor, rope slam) for 21:9.
6. **Founder portraits** — one still, unsmiling, direct to lens; one in profile.
7. **Interview** — the football → setback → rebuild story in his words, 2–3 minutes.
8. **Closing** — silhouette against a bright doorway or sunrise; end on stillness.
