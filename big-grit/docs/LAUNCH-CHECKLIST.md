# Launch-readiness checklist

## ✅ Done and tested in this build

- [x] All sections built: opening sequence, philosophy, founder story, coaching, Find Your Grit, results, playbooks (config-driven), application, closing, footer, privacy, 404
- [x] Opening sequence engine: video + stills, masked wipes, progress, captions, pause control; desktop/mobile sources; pauses off-screen
- [x] Tested with real MP4 files: muted autoplay, sequence advances, phones load the vertical file, Save-Data users get stills only
- [x] Find Your Grit: 5 questions (preserved from the original concept), keyboard 1–4, back, result, recommendation; answers + lead priority carried into the application
- [x] Application: validation, consent, honeypot, UTM/referrer/landing-page attribution, honest error states, confirmation, optional booking hand-off
- [x] Submission tested: real failure shows an error and keeps the form; mocked Netlify acceptance shows confirmation with a complete payload
- [x] Conversion events: `cta_click`, `assessment_start`, `assessment_complete`, `generate_lead`, `booking_click`, `product_click`, `film_play`
- [x] Mobile: no horizontal overflow (iPhone 13 / Pixel 7 emulation), apply dock, full-screen menu
- [x] Reduced-motion and no-JavaScript fallbacks verified; axe accessibility scan clean except the decorative footer wordmark
- [x] SEO: titles, description, Open Graph/Twitter card + image, JSON-LD, sitemap, robots, favicon/app icons
- [x] Initial load ≈ 490 KB (fonts + libraries self-hosted; no third-party requests until analytics are configured)

## 🔴 Required before going public

- [x] Video: three hero clips (desktop + vertical phone versions), the "No shortcuts" band, the philosophy film and the closing film, cut from the 4K Club Studio footage
- [x] Real photography in every section (earlier rendering + Google Photos albums); `showPlaceholders` is already `false`
- [ ] **Client photos** — confirm each client in the three before/afters agreed in writing to appear on the site
- [ ] **Payhip** — make a real test purchase of each playbook and confirm the PDF arrives (the earlier version noted checkout was still being tested)
- [ ] **Football-era photo** for chapter 01 (Syracuse / Colts) — only if you hold usage rights
- [ ] **Deploy to Netlify, enable form detection, add email notification, send a live test application** (see DEPLOY.md §2)
- [x] `contactEmail`, Instagram, online application and macro calculator links set in `config/site.config.js` (confirm the email is the one Delone wants public)
- [ ] **Domain** connected + `./scripts/set-domain.sh yourdomain.com`
- [ ] **Delone reviews all copy**, in particular:
  - founder chapters — facts now come from the earlier Big Grit version (Pinstripe Bowl MVP at Syracuse, Colts 2011 draft, hip-replacement verdict, IFBB Pro card in Men's Physique)
  - coaching descriptions (in-person 1:1, online "structure, programming and accountability")
  - the assessment's result lines and "Your application goes straight to Delone"
- [ ] **Privacy policy** reviewed for your tools and location

## 🟡 Strongly recommended for launch weekend

- [ ] `bookingUrl` (Calendly / Cal.com) — turns applications into booked consultations immediately
- [ ] GA4 and/or Meta Pixel IDs; mark `generate_lead` as a key event
- [ ] Closing film + "No shortcuts" detail band footage
- [ ] Chapter photos 01–04 (NFL-era photo: confirm usage rights)
- [x] Investment shown from Delone's own application ($1,500–$5,000 online; private rates confirmed directly)
- [ ] Test on a real iPhone (Low Power Mode blocks autoplay → poster is shown; that's expected)

## 🟢 After launch (future enhancements)

- [ ] Written client testimonials (real words, with permission)
- [ ] Founder film (MP4 or YouTube/Vimeo link)
- [ ] Automated follow-up emails (24 h / 72 h) and CRM — the automation plan from the original concept; connect via Netlify form notification → Zapier/Make → your CRM/email tool
- [ ] Dedicated landing pages: "Personal trainer Dallas", "Online coaching" for search
- [ ] Google Business Profile + reviews
- [ ] Content-Security-Policy header once analytics choices are final
- [ ] Cookie consent banner if you advertise to EU/UK/California audiences

## External accounts & credentials needed

| Item | Needed for | Where it goes |
|---|---|---|
| Netlify account | hosting + forms | — |
| Domain registrar login | DNS records | DEPLOY.md §3 |
| Business email address | notifications, fallback | Netlify form notifications + `contactEmail` |
| Calendly / Cal.com link | consultation booking | `bookingUrl` |
| GA4 Measurement ID | analytics | `analytics.ga4MeasurementId` |
| Meta Pixel ID | ad conversion tracking | `analytics.metaPixelId` |
| Stripe Payment Links / Shopify / Gumroad | product sales | `products[].checkoutUrl` |
| (optional) Formspree endpoint or Zapier/Make webhook | forms outside Netlify | `forms` |

No secrets are stored in the site — all IDs above are public by design.
