# Big Grit Performance — flagship website

A fast, static, production-ready website for **Big Grit Performance** (Delone Carter): cinematic opening sequence, founder story, coaching paths, the *Find Your Grit* assessment, a real application form and a closing campaign statement.

No build step, no server, no database. Upload the folder and it runs.

```
big-grit/
├── index.html                 ← the website (all copy lives here, in plain HTML)
├── privacy.html  404.html
├── config/
│   ├── site.config.js         ← BUSINESS SETTINGS: email, form provider, booking link, analytics, products, testimonials
│   └── media.config.js        ← EVERY PHOTO & VIDEO SLOT, with required sizes
├── media/                     ← drop footage & photos here (see docs/MEDIA-GUIDE.md)
│   ├── hero/ training/ details/ founder/ interview/
│   ├── editorial/ clients/ transformations/ closing/ products/ og/
├── assets/
│   ├── css/main.css           ← design system + layout
│   ├── js/main.js             ← engine (media loader, hero sequence, motion, assessment, form)
│   ├── js/vendor/             ← GSAP 3.13 + ScrollTrigger, Lenis 1.3 (self-hosted)
│   └── fonts/                 ← Archivo (variable) + Instrument Serif Italic (self-hosted)
├── scripts/
│   ├── optimize-media.sh      ← compress camera footage/photos for the web (ffmpeg)
│   └── set-domain.sh          ← write your domain into SEO/social tags
├── docs/                      ← guides (not served publicly on Netlify)
└── netlify.toml               ← hosting, caching & security headers
```

## Start here

| I want to… | Read |
|---|---|
| Start here (for Delone) | [`START-HERE.md`](START-HERE.md) |
| Swap photos and videos | [`docs/MEDIA-GUIDE.md`](docs/MEDIA-GUIDE.md) |
| Put the site online and connect the domain | [`docs/DEPLOY.md`](docs/DEPLOY.md) |
| Know what's left before launch | [`docs/LAUNCH-CHECKLIST.md`](docs/LAUNCH-CHECKLIST.md) |
| Understand the design & motion system | [`docs/CREATIVE-DIRECTION.md`](docs/CREATIVE-DIRECTION.md) |

## Preview locally

From inside this folder:

```bash
python3 -m http.server 8080
# then open http://localhost:8080
```

(Opening `index.html` by double-clicking works for a rough look, but media detection and the form need a real web server.)

## What's real vs. pending

* **Working now:** every section, animation, navigation, the assessment, form validation, source/UTM capture, the mobile apply dock, reduced-motion & no-JavaScript fallbacks, SEO/social metadata.
* **Works once hosted on Netlify:** application submissions (Netlify Forms). Elsewhere, set Formspree or a webhook in `config/site.config.js`.
* **Waiting on you:** footage and photography (labelled slots), contact email, booking link, analytics IDs, domain. Nothing is faked — unconfigured features hide themselves or say they're not connected.
