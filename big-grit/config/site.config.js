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
  contactEmail: "",
  // Full Instagram URL, e.g. "https://www.instagram.com/yourhandle/"
  instagramUrl: "",
  // Optional extra socials (leave "" to hide)
  youtubeUrl: "",
  tiktokUrl: "",
  location: "Dallas–Fort Worth, Texas",

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
  pricingNote: "",

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
   * The original site file contained NO digital products, so this list
   * starts empty and the Playbooks section is hidden.
   * To sell a guide, copy the example below (remove the // marks).
   * checkoutUrl must be a real checkout: Stripe Payment Link, Shopify,
   * Gumroad, Stan Store, Lemon Squeezy, etc.
   * --------------------------------------------------------------------- */
  products: [
    // {
    //   title: "The Grit Playbook",
    //   format: "PDF guide · 12-week program",
    //   description: "One honest sentence about what the buyer gets.",
    //   price: "$49",
    //   image: "media/products/grit-playbook.jpg",   // 1200×1500 cover
    //   checkoutUrl: "https://buy.stripe.com/your-link",
    //   ctaLabel: "Get the playbook",
    // },
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
