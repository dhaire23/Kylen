# Launch checklist — Big Grit Performance

Full test results are in `AUDIT.md`. Everything below marked ✅ is built and tested; the open items need a person, an account, or a live deploy.

## ✅ Built and tested

- [x] Seven-part opening (3 graded 4K clips + 4 photographs), seamless video/photo cuts, headline and CTAs anchored, vertical clips on phones
- [x] Visual rhythm: full-bleed photo interlude, cinematic video interlude, two light editorial sections (Results, Playbooks)
- [x] Founder story ("Told no. Three times.") + The Tape, an auto-playing career reel (High school → Syracuse → Colts → IFBB Pro)
- [x] Find Your Grit assessment → recommendation → pre-filled application (all answer paths tested)
- [x] Application: validation, consent, honeypot, UTM/referrer capture, lead priority, honest errors, confirmation
- [x] Three playbooks with Payhip checkout links
- [x] Editorial client case studies (verified details only)
- [x] Context-aware mobile apply bar (hidden during the assessment, application and typing)
- [x] Mobile readability pass (larger body text, stronger muted text, 44 px+ tap targets)
- [x] Reduced-motion, autoplay-blocked, Data Saver and no-JavaScript behaviour
- [x] Accessibility (WCAG 2 A/AA, axe) · no sideways scroll at 320–1920 px · LCP within 2.5 s (desktop and 4G phone)
- [x] SEO: titles, description, social card, structured data, sitemap, robots, icons
- [x] Redirects so old `/application` and `/macro-calculator` links land on the new site

## 🔴 Required before going public

- [ ] **Domain**: confirm which domain launches (getgritfit.com is currently on GoHighLevel; see `DEPLOY.md` §3), connect it in Netlify, then run `./scripts/set-domain.sh yourdomain.com`
- [ ] **Deploy to Netlify** → enable form detection → add an email notification → **send one test application** and confirm it arrives
- [ ] **Payhip**: one real test purchase per playbook; confirm the PDF arrives
- [ ] **Client consent**: written OK from each client in the before/afters (`CASE-STUDIES.md`)
- [ ] **Delone's review** of all copy: story facts, coaching descriptions, the $1,500–$5,000 line, FAQ answers
- [ ] **Contact email**: confirm `thecarter2287@gmail.com` is the public address
- [ ] **Live check on real phones**: one iPhone (Safari) and one Android (Chrome); watch The Tape and set each football clip's best seconds (`start`/`end` in `careerReel`)
- [ ] **Privacy policy** reviewed for your tools and location

## 🟡 Recommended for launch weekend

- [ ] Booking link (Calendly / Cal.com) → `bookingUrl`, so applicants can book a call right away
- [ ] GA4 and/or Meta Pixel IDs → `analytics`; mark `generate_lead` as a key event
- [ ] If Delone uses **GoHighLevel** as his CRM: send applications straight into it (`forms.provider: "webhook"`, GoHighLevel inbound-webhook URL)
- [ ] If Delone uses **Trainerize**: add the sign-up link as the next step after applying
- [ ] Case-study details collected and entered (`CASE-STUDIES.md`)

## 🟢 After launch

- [ ] Founder interview film (MP4 or YouTube link), NFL-era photo (with usage rights)
- [ ] Written client testimonials
- [ ] Re-enable the getgritfit.com application / macro calculator links if those pages come back
- [ ] Google Business Profile + reviews; search landing pages ("Personal trainer Dallas", "Online coaching")
- [ ] Follow-up email automation (24 h / 72 h) through the CRM
- [ ] Content-Security-Policy header once analytics are final; cookie banner if advertising to EU/UK/California

## External accounts, credentials and assets still needed

| Item | Needed for | Who | Where it goes |
|---|---|---|---|
| Netlify account | hosting, forms | you / Delone | DEPLOY.md §1 |
| Domain registrar or GoHighLevel domain access | pointing the domain at Netlify | owner of the domain | DEPLOY.md §3 |
| Inbox for applications | form notifications | Delone | Netlify → Forms → Notifications |
| Payhip account access | test purchases, PDFs | Delone | — |
| Calendly / Cal.com link | booking | Delone | `bookingUrl` |
| GA4 Measurement ID, Meta Pixel ID | tracking | whoever runs ads | `analytics` |
| GoHighLevel inbound webhook URL (optional) | leads into CRM | Delone | `forms.webhookUrl` |
| Trainerize sign-up link (optional) | online-client onboarding | Delone | ask Claude to wire it in |
| Client consents + case-study details | Results section | Delone | `CASE-STUDIES.md` |

No passwords or secret keys live in the website; every ID above is public by design.
