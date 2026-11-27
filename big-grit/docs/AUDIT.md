# Production-readiness audit — 27 November 2026

Automated browser tests (Chromium, desktop 1440×900 plus Galaxy S9+ and iPhone 13 emulation) against the final build.
**Result: every automated check passes.** What could *not* be verified from the build environment is listed at the end. Those items need a person, a live deploy, or both.

## Summary

| Area | Checks | Result |
|---|---|---|
| Navigation (desktop links, mobile menu, footer) | 15 | ✅ all land on their section; menu closes |
| Find Your Grit logic + application prefill (6 answer paths covering every answer) | 6 | ✅ correct recommendation, format, goal, timeline and lead priority each time |
| Hero quick-start → question 2 → application | 3 | ✅ |
| Application form: validation, honest error, success state, full payload | 8 | ✅ |
| Mobile apply bar (context-aware) | 6 | ✅ hides during the assessment, at the application and while typing; returns after |
| Media: hero, band, philosophy, interlude, closing, career reel | 12 | ✅ all play; phones get vertical clips; no broken images or missing files |
| Career reel (The Tape): autoplay in view, advance, loop, sound, pause, jump, YouTube-blocked fallback | 14 | ✅ (YouTube's player simulated; see limits) |
| Reduced motion, autoplay refused, no JavaScript | 8 | ✅ page stays readable; footage still plays where allowed |
| Responsive, no sideways scroll or cut-off content at 320 · 360 · 375 · 390 · 414 · 768 · 1024 · 1280 · 1440 · 1920 px | 10 | ✅ (320 px fixed during this audit) |
| Accessibility (axe-core, WCAG 2 A/AA), desktop + mobile | 2 | ✅ no violations (the outlined footer wordmark is decorative and hidden from screen readers) |
| Performance | 2 | ✅ see below |
| Static: duplicate IDs, in-page links, local files, form fields, video encoding | — | ✅ 0 duplicate IDs · 10/10 anchors · all files present · 20 form fields registered for Netlify · 14 videos H.264 "fast start", 2–4.5 MB |

## Performance

| Scenario | First paint | Largest paint (LCP) | Layout shift (CLS) |
|---|---|---|---|
| Desktop | 0.2 s | 2.2 s | 0.001 |
| Phone on 4G, first visit (brand reveal) | 0.46 s | 0.46 s | 0 |
| Phone on 4G, return visit | — | 2.4 s | 0 |

Changes made during the audit: lighter entrance photos (−550 KB), later hero clips and YouTube's player now load only when needed, the brand reveal is skipped on 3G or Data Saver, and the intro and opening window are about 30% shorter.

## External links (checked 27 Nov 2026)

| Link | Status |
|---|---|
| YouTube: the three career-reel videos | ✅ online, embeddable |
| Instagram: @delonecarter_ifbbpro | ✅ exists (Instagram asks logged-out visitors to sign in) |
| Payhip: Muscle Gain, Fat Loss Mastery, Built to Bounce Back | ⚠️ could not verify: Payhip's Cloudflare check blocks automated browsers. **Do one real test purchase per playbook.** |
| getgritfit.com/application and /macro-calculator | ❌ **404 Page Not Found** (served by GoHighLevel; pages unpublished). The site now uses its own application form for online coaching and hides the macro-calculator link. Re-enable in `config/site.config.js` once the pages are live. |

## Not verifiable from the build environment

1. **Real lead delivery.** The form's payload and Netlify registration are verified, but an application only reaches an inbox once the site is live on Netlify with form notifications on. **Send one test application after deploying.**
2. **Payhip checkout and PDF delivery.** One real purchase each (see above).
3. **YouTube playback inside the site.** The career reel was tested with a simulated YouTube player; the real one is blocked in this environment. **Watch The Tape once on the live site.**
4. **Real phones.** Tests used emulation. Check the live site on one iPhone (Safari) and one Android (Chrome), including with Low Power Mode and Reduce Motion on.
5. **Your domain and DNS.** See `DEPLOY.md`.
