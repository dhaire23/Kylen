/* ==========================================================================
   BIG GRIT PERFORMANCE — SITE SETTINGS
   --------------------------------------------------------------------------
   This is the ONE file for business settings: contact details, the form
   provider, booking link, analytics and digital products.

   Rules for editing (no coding knowledge needed):
   • Only change the text between the quotes "like this".
   • Keep every comma at the end of a line.
   • Leave a value as "" (empty quotes) to switch that feature off —
     the website hides anything that is not configured.
   ========================================================================== */

window.BIG_GRIT_SITE = {

  /* ---------- Brand & contact ------------------------------------------ */
  brandName: "Big Grit Performance",
  // Public email shown as a fallback if a form ever fails. e.g. "coach@yourdomain.com"
  contactEmail: "thecarter2287@gmail.com",
  // Full Instagram URL, e.g. "https://www.instagram.com/yourhandle/"
  instagramUrl: "https://www.instagram.com/delonecarter_ifbbpro/",
  // Optional extra socials (leave "" to hide)
  youtubeUrl: "",
  tiktokUrl: "",
  location: "Dallas–Fort Worth, Texas",

  /* ---------- Existing GritFit pages (opened in a new tab) ------------- *
   * onlineApplicationUrl: Delone's online coaching application. When set,
   *   "Apply for online coaching" buttons go straight to it.
   * macroCalculatorUrl: free macro calculator, offered as a free tool.
   * --------------------------------------------------------------------- */
  // getgritfit.com (GoHighLevel) returned "404 Page Not Found" on 27 Nov 2026,
  // so these are switched off: online coaching uses this site's own application
  // form instead. Paste the links back in once those pages are live again.
  onlineApplicationUrl: "",   // was "https://www.getgritfit.com/application"
  macroCalculatorUrl: "",     // was "https://www.getgritfit.com/macro-calculator"

  /* ---------- Application form ----------------------------------------- *
   * provider:
   *   "netlify"   → Netlify Forms. Works automatically when the site is
   *                 hosted on Netlify. No account keys needed. (RECOMMENDED)
   *   "formspree" → Formspree.io. Paste your form endpoint below.
   *   "webhook"   → Any URL that accepts JSON (Zapier, Make, GoHighLevel,
   *                 HubSpot, your CRM). Paste it below.
   *   "none"      → Form shows a clear "not connected yet" message.
   * The site NEVER pretends a submission worked. If sending fails, the
   * visitor sees an error and (if set) your contactEmail.
   * --------------------------------------------------------------------- */
  forms: {
    provider: "netlify",
    formspreeEndpoint: "",   // e.g. "https://formspree.io/f/abcdwxyz"
    webhookUrl: "",          // e.g. "https://hooks.zapier.com/hooks/catch/123/abc/"
  },

  /* ---------- Consultation booking ------------------------------------ *
   * Paste your Calendly / Cal.com / Acuity / Square booking link.
   * When set, a "Book your consultation" button appears after a
   * successful application (name + email are passed to Calendly/Cal.com).
   * --------------------------------------------------------------------- */
  bookingUrl: "",

  /* ---------- Pricing note (optional) ---------------------------------- *
   * One honest line shown under the coaching options, e.g.
   * "In-person packages from $X. Online coaching from $Y / month."
   * Leave "" to show nothing.
   * --------------------------------------------------------------------- */
  pricingNote: "Online coaching: Delone works with clients ready to invest $1,500 to $5,000. Private training rates are confirmed with Delone directly.",

  /* ---------- Analytics & conversion tracking ------------------------- *
   * Leave "" to disable. Scripts only load when an ID is present.
   * Events sent: cta_click, assessment_start, assessment_complete,
   * generate_lead (application submitted), booking_click, product_click.
   * --------------------------------------------------------------------- */
  analytics: {
    ga4MeasurementId: "",    // e.g. "G-XXXXXXXXXX"
    metaPixelId: "",         // e.g. "123456789012345"
  },

  /* ---------- Digital products (guides, playbooks) --------------------- *
   * Delone's three $25 playbooks, sold and delivered through Payhip.
   * Remove an entry to stop showing it; empty the list to hide the section.
   * checkoutUrl must be a real checkout: Stripe Payment Link, Shopify,
   * Gumroad, Stan Store, Lemon Squeezy, etc.
   * --------------------------------------------------------------------- */
  products: [
    {
      title: "Muscle Gain",
      format: "Self-guided PDF · Build",
      description: "Mindset, training and nutrition fundamentals for putting on muscle. GritFit Gospel: for warriors, not wishers.",
      price: "$25",
      image: "media/products/muscle-gain.webp",
      checkoutUrl: "https://payhip.com/b/WcaFE",
      ctaLabel: "Buy on Payhip",
    },
    {
      title: "Fat Loss Mastery",
      format: "Self-guided PDF · Lean out",
      description: "Calorie balance, consistency and habits you can keep. No myths, no magic: the math and the discipline it takes to get lean and stay that way.",
      price: "$25",
      image: "media/products/fat-loss-mastery.webp",
      checkoutUrl: "https://payhip.com/b/jLk0r",
      ctaLabel: "Buy on Payhip",
    },
    {
      title: "Built to Bounce Back",
      format: "Self-guided PDF · Recover",
      description: "Recovery, fueling and sleep for the hours outside the gym — for lifters who don't take days off. Not a medical or rehabilitation program.",
      price: "$25",
      image: "media/products/built-to-bounce-back.webp",
      checkoutUrl: "https://payhip.com/b/5UHCF",
      ctaLabel: "Buy on Payhip",
    },
  ],

  /* ---------- Client testimonials ------------------------------------- *
   * ONLY real, verbatim quotes from real clients who gave permission.
   * Leave empty until you have them — nothing is shown when empty.
   * --------------------------------------------------------------------- */
  testimonials: [
    // {
    //   quote: "Exact words from the client.",
    //   name: "First name L.",
    //   detail: "Online coaching · 16 weeks",
    // },
  ],
};
