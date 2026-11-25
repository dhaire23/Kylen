# Deploying Big Grit

**Recommended host: Netlify** (free tier is enough). It serves the static files, runs the application form with zero setup (Netlify Forms), emails you each submission, and gives free HTTPS.

## 1 · Publish (≈10 minutes)

**Option A — drag & drop (no tools)**
1. Create a free account at netlify.com.
2. *Sites → Add new site → Deploy manually*.
3. Drag the whole `big-grit` folder onto the page. You get a live `something.netlify.app` URL.
4. To update later: *Deploys → drag the folder in again*.

**Option B — from GitHub (auto-deploys on every change)**
1. *Add new site → Import from Git →* choose this repository.
2. Base directory: `big-grit` · Build command: *(leave empty)* · Publish directory: `big-grit`.
3. Deploy. Every push to the branch republishes.

## 2 · Turn on the application form

1. After the first deploy: *Site configuration → Forms → Enable form detection*, then **redeploy once**. A form named **application** appears.
2. *Forms → Form notifications → Add notification → Email notification* → your inbox. Optionally add Slack or a webhook (e.g. to your CRM).
3. Test it: submit the live form with your own email and confirm the notification arrives. (Spam is filtered by a hidden honeypot field plus Netlify's spam filter.)

Free tier: 100 submissions/month. If you outgrow it, upgrade or switch provider (below).

**Not on Netlify?** In `config/site.config.js` set `provider: "formspree"` and paste your Formspree endpoint, or `provider: "webhook"` with a Zapier / Make / GoHighLevel catch URL (it must accept form-encoded POSTs from browsers — Zapier and Make do).

## 3 · Connect your domain

1. Netlify: *Domain management → Add a domain* → enter your domain (e.g. `biggritperformance.com`).
2. Either switch your domain's nameservers to Netlify (simplest) **or** at your registrar add:
   * `A` record `@` → `75.2.60.5`
   * `CNAME` record `www` → `your-site.netlify.app`
   (Use the exact values Netlify shows you — they take priority over this note.)
3. HTTPS is issued automatically within minutes to a few hours.
4. Write the domain into the SEO/social tags, then redeploy:
   ```bash
   ./scripts/set-domain.sh yourdomain.com
   ```
   (Or find-and-replace `YOUR-DOMAIN.com` in `index.html`, `robots.txt`, `sitemap.xml`.)

## 4 · Booking, email, analytics

Edit `config/site.config.js` (instructions inside the file):

| Setting | What it does |
|---|---|
| `contactEmail` | Shown in the footer, privacy page and as the fallback if a form ever fails |
| `instagramUrl` (+ YouTube/TikTok) | Social links in menu & footer |
| `bookingUrl` | Calendly / Cal.com / Acuity link. After applying, the visitor gets a "Book your consultation" button (name + email prefilled for Calendly/Cal.com) |
| `analytics.ga4MeasurementId` | Google Analytics 4. Mark `generate_lead` as a **key event** in GA4 |
| `analytics.metaPixelId` | Meta Pixel. Fires `Lead` on application, `Schedule` on booking click |
| `products` | Adds the Playbooks section with real checkout links |
| `testimonials` | Real client quotes only |

Every conversion also goes to `window.dataLayer`, so Google Tag Manager works if you prefer it.

## 5 · After launch

* Submit `https://yourdomain.com/sitemap.xml` in Google Search Console.
* Set up / update your **Google Business Profile** for Dallas–Fort Worth and link it to the site.
* Share links with UTM tags so applications show their source, e.g. `https://yourdomain.com/?utm_source=instagram&utm_medium=bio&utm_campaign=launch` — the source is attached to every application.

## Other hosts

The site is plain static files and works on Cloudflare Pages, Vercel, GitHub Pages or any web host. Only the Netlify Forms option is Netlify-specific; use Formspree or a webhook elsewhere. Keep `404.html` at the root — the media detector relies on missing files returning a real 404.
