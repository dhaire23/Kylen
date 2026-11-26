/* ==========================================================================
   BIG GRIT PERFORMANCE — site engine
   You should not need to edit this file. Business settings live in
   config/site.config.js and all photos/videos in config/media.config.js.
   --------------------------------------------------------------------------
   01 Setup  02 Helpers  03 Analytics  04 Media slots  05 Hero sequence
   06 Intro  07 Smooth scroll & nav  08 Motion  09 Founder  10 Coaching
   11 Assessment  12 Results & products  13 Application form  14 Boot
   ========================================================================== */
(function () {
  'use strict';

  /* 01 · Setup ------------------------------------------------------------ */
  var SITE = window.BIG_GRIT_SITE || {};
  var MEDIA = window.BIG_GRIT_MEDIA || {};
  var LIB = MEDIA.library || {};
  var root = document.documentElement;
  var mq = function (q) { return window.matchMedia(q).matches; };
  var reduceMotion = mq('(prefers-reduced-motion: reduce)');
  var conn = navigator.connection || {};
  var saveData = !!(conn.saveData || /(^|-)2g$/.test(conn.effectiveType || ''));
  var hasGSAP = !!(window.gsap && window.ScrollTrigger);
  var motion = hasGSAP && !reduceMotion;
  var gsap = window.gsap;
  var ST = window.ScrollTrigger;
  var lenis = null;
  var placeholders = MEDIA.showPlaceholders === true;

  window.__BG_READY = true;
  if (motion) {
    gsap.registerPlugin(ST);
    root.classList.add('motion');
  } else {
    root.classList.remove('motion', 'intro-pending');
  }

  /* 02 · Helpers ---------------------------------------------------------- */
  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function getPath(obj, path) {
    return path.split('.').reduce(function (o, k) { return o == null ? undefined : o[k]; }, obj);
  }
  function store(kind) {
    try { return window[kind]; } catch (e) { return null; }
  }
  function sGet(k) { try { var s = store('sessionStorage'); return s ? s.getItem(k) : null; } catch (e) { return null; } }
  function sSet(k, v) { try { var s = store('sessionStorage'); if (s) s.setItem(k, v); } catch (e) { /* storage blocked */ } }
  function sDel(k) { try { var s = store('sessionStorage'); if (s) s.removeItem(k); } catch (e) { /* storage blocked */ } }
  function absUrl(u) { try { return new URL(u, location.href).href; } catch (e) { return u; } }
  function isPortraitPhone() { return mq('(max-width: 900px) and (orientation: portrait)'); }
  function isSafeUrl(u) { return typeof u === 'string' && /^(https?:\/\/|mailto:|\/|[a-z0-9_\-./]+$)/i.test(u.trim()) && !/^javascript:/i.test(u.trim()); }

  /* Does a media file exist? HEAD request; treats HTML responses (soft 404s) as missing.
     Returns true / false, or null when it cannot tell (e.g. opened from disk). */
  var probeCache = {};
  function probe(url) {
    if (!url) return Promise.resolve(false);
    if (probeCache[url]) return probeCache[url];
    if (location.protocol === 'file:') return (probeCache[url] = Promise.resolve(null));
    probeCache[url] = fetch(url, { method: 'HEAD', cache: 'no-cache' }).then(function (r) {
      var type = r.headers.get('content-type') || '';
      return r.ok && type.indexOf('text/html') === -1;
    }).catch(function () { return null; });
    return probeCache[url];
  }

  /* 03 · Analytics -------------------------------------------------------- */
  window.dataLayer = window.dataLayer || [];
  function loadAnalytics() {
    var a = SITE.analytics || {};
    if (a.ga4MeasurementId && /^G-[A-Z0-9]+$/i.test(a.ga4MeasurementId)) {
      var s = document.createElement('script');
      s.async = true;
      s.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(a.ga4MeasurementId);
      document.head.appendChild(s);
      window.gtag = function () { window.dataLayer.push(arguments); };
      window.gtag('js', new Date());
      window.gtag('config', a.ga4MeasurementId);
    }
    if (a.metaPixelId && /^\d+$/.test(a.metaPixelId)) {
      /* Standard Meta Pixel bootstrap */
      (function (f, b, e, v, n, t, s) {
        if (f.fbq) return; n = f.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
        if (!f._fbq) f._fbq = n; n.push = n; n.loaded = !0; n.version = '2.0'; n.queue = [];
        t = b.createElement(e); t.async = !0; t.src = v; s = b.getElementsByTagName(e)[0]; s.parentNode.insertBefore(t, s);
      })(window, document, 'script', 'https://connect.facebook.net/en_US/fbevents.js');
      window.fbq('init', a.metaPixelId);
      window.fbq('track', 'PageView');
    }
  }
  var META_EVENTS = { generate_lead: 'Lead', booking_click: 'Schedule', product_click: 'InitiateCheckout' };
  function track(name, params) {
    params = params || {};
    window.dataLayer.push(Object.assign({ event: name }, params));
    if (typeof window.gtag === 'function') window.gtag('event', name, params);
    if (typeof window.fbq === 'function') {
      if (META_EVENTS[name]) window.fbq('track', META_EVENTS[name], params);
      else window.fbq('trackCustom', name, params);
    }
  }
  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-track]');
    if (el) track(el.getAttribute('data-track'), { cta: el.getAttribute('data-cta') || el.textContent.trim().slice(0, 40) });
  });

  /* 04 · Media slots -----------------------------------------------------
     <div class="media" data-slot="founder.portrait"></div> is filled from
     media.config.js. Missing files fall back gracefully.                  */
  function pick(item) {
    var mobile = isPortraitPhone();
    return (mobile && item.srcMobile) ? item.srcMobile : item.src;
  }

  function placeholderHTML(key, item, over) {
    var src = item && (item.src || item.before) || '';
    return '<div class="ph' + (over ? ' ph--over' : '') + '"><b>Media slot · ' + esc(key) + '</b>' +
      (src ? '<code>' + esc(src) + '</code>' : '') +
      (item && item.spec ? '<i>' + esc(item.spec) + '</i>' : '') + '</div>';
  }

  function makeImg(src, alt, opts) {
    opts = opts || {};
    var img = new Image();
    img.decoding = 'async';
    if (!opts.eager) img.loading = 'lazy';
    img.alt = alt || '';
    img.src = src;
    return img;
  }

  function imageNode(item, eager) {
    var img = makeImg(item.src, item.alt, { eager: eager });
    if (item.width) { img.width = item.width; img.height = item.height; }
    if (!item.webp) return { node: img, img: img };
    var pic = document.createElement('picture');
    var source = document.createElement('source');
    source.type = 'image/webp';
    source.srcset = item.webp;
    pic.appendChild(source);
    pic.appendChild(img);
    return { node: pic, img: img };
  }

  /* Wide container + portrait photo → show it sharp at full height over a blurred copy */
  function smartFrame(el, img) {
    function check() {
      if (!img.naturalWidth || !el.clientHeight) return;
      var boxAR = el.clientWidth / el.clientHeight;
      var imgAR = img.naturalWidth / img.naturalHeight;
      var framed = boxAR / imgAR > 1.45;
      el.classList.toggle('is-framed', framed);
      var bd = $('.mf-backdrop', el);
      if (framed && !bd) {
        bd = document.createElement('div');
        bd.className = 'mf-backdrop';
        bd.style.setProperty('--bg', 'url("' + absUrl(img.currentSrc || img.src) + '")');
        el.insertBefore(bd, el.firstChild);
      }
    }
    if (img.complete) check(); else img.addEventListener('load', check, { once: true });
    frameChecks.push(check);
  }
  var frameChecks = [];
  window.addEventListener('resize', debounce(function () { frameChecks.forEach(function (f) { f(); }); }, 200));

  function renderImage(el, item, state, eager) {
    var n = imageNode(item, eager);
    n.img.addEventListener('error', function () {
      if (state === 'asset') renderFallback(el, el.getAttribute('data-slot'), item);
    }, { once: true });
    el.appendChild(n.node);
    el.setAttribute('data-state', state);
    if (item.focus) el.style.setProperty('--focus', item.focus);
    smartFrame(el, n.img);
    n.img.addEventListener('load', refreshSoon, { once: true });
  }

  function renderVideo(el, item, src) {
    var v = document.createElement('video');
    v.muted = true; v.defaultMuted = true; v.loop = true; v.playsInline = true;
    v.setAttribute('muted', ''); v.setAttribute('playsinline', ''); v.setAttribute('webkit-playsinline', '');
    v.preload = reduceMotion ? 'metadata' : 'none';
    v.setAttribute('aria-hidden', 'true');
    v.setAttribute('disablepictureinpicture', '');
    if (item.poster) v.poster = item.poster;
    v.src = src;
    v.addEventListener('error', function () { v.remove(); renderFallback(el, el.getAttribute('data-slot'), item); }, { once: true });
    el.appendChild(v);
    el.setAttribute('data-state', 'asset');
    if (item.focus) el.style.setProperty('--focus', item.focus);
    if (!reduceMotion) videoIO.observe(v);
  }

  function renderFallback(el, key, item) {
    el.innerHTML = '';
    var fb = item && item.fallback ? LIB[item.fallback] : null;
    if (fb) renderImage(el, fb, placeholders ? 'placeholder' : 'fallback');
    if (placeholders) {
      el.insertAdjacentHTML('beforeend', placeholderHTML(key, item, !!fb));
      el.setAttribute('data-state', 'placeholder');
    } else if (!fb) {
      el.setAttribute('data-state', 'empty');
    }
    afterSlot(el);
  }

  function afterSlot(el) {
    var cb = el.__onSlot;
    if (typeof cb === 'function') cb(el.getAttribute('data-state'));
    refreshSoon();
  }

  function fillSlot(el) {
    if (el.__filled) return;
    el.__filled = true;
    var key = el.getAttribute('data-slot');
    var item = getPath(MEDIA, key);
    if (!item) { renderFallback(el, key, null); return; }
    var useVideo = item.type === 'video' && !saveData;
    var src = useVideo ? pick(item) : (item.type === 'video' ? item.poster : item.src);
    probe(src).then(function (ok) {
      if (ok === false && useVideo && src !== item.src) { src = item.src; return probe(src); }
      return ok;
    }).then(function (ok) {
      if (ok === false) { renderFallback(el, key, item); return; }
      if (useVideo) renderVideo(el, item, src);
      else renderImage(el, { src: src, alt: item.alt, focus: item.focus, fallback: item.fallback, spec: item.spec }, 'asset');
      afterSlot(el);
    });
  }

  var videoIO = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      var v = en.target;
      if (en.isIntersecting) {
        if (v.preload === 'none') { v.preload = 'auto'; }
        var p = v.play(); if (p && p.catch) p.catch(function () { /* autoplay refused: poster stays */ });
      } else if (!v.paused) v.pause();
    });
  }, { threshold: 0.12 }) : { observe: function () {} };

  var slotIO = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (en) { if (en.isIntersecting) { slotIO.unobserve(en.target); fillSlot(en.target); } });
  }, { rootMargin: '900px 0px' }) : null;

  function initSlots() {
    $$('[data-slot]').forEach(function (el) {
      if (slotIO) slotIO.observe(el); else fillSlot(el);
    });
  }

  var refreshTimer;
  function refreshSoon() {
    if (!motion) return;
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(function () { ST.refresh(); }, 250);
  }
  function debounce(fn, ms) { var t; return function () { clearTimeout(t); t = setTimeout(fn, ms); }; }

  /* 05 · Hero sequence ----------------------------------------------------
     Cycles through MEDIA.heroSequence (videos + stills) with masked wipes.
     The poster in the HTML paints instantly; slides join as they load.    */
  var hero = { slides: [], current: -1, paused: false, visible: true, timer: null, started: false };

  function initHero() {
    var wrap = $('[data-hero-slides]');
    var list = (MEDIA.heroSequence || []).filter(function (i) { return i && i.src; });
    if (!wrap || !list.length) return;

    list.forEach(function (item, idx) {
      var slide = { item: item, ready: false, el: null, media: null, video: null, idx: idx };
      hero.slides.push(slide);
      var isVideo = item.type === 'video';
      if (isVideo && (saveData || reduceMotion)) {
        // Still frames only: use the video's poster if it exists.
        if (!item.poster) return;
        probe(item.poster).then(function (ok) { if (ok !== false) buildImageSlide(slide, wrap, { src: item.poster, fit: 'cover', focus: item.focus, zoom: 1 }); });
        return;
      }
      if (isVideo) {
        var src = pick(item);
        probe(src).then(function (ok) {
          if (ok === false && src !== item.src) { src = item.src; return probe(src); }
          return ok;
        }).then(function (ok) { if (ok !== false) buildVideoSlide(slide, wrap, src); });
      } else {
        probe(item.src).then(function (ok) { if (ok !== false) buildImageSlide(slide, wrap, item); });
      }
    });

    var pauseBtn = $('[data-hero-pause]');
    if (pauseBtn) pauseBtn.addEventListener('click', function () {
      hero.paused = !hero.paused;
      pauseBtn.setAttribute('aria-pressed', String(hero.paused));
      pauseBtn.setAttribute('aria-label', hero.paused ? 'Play background motion' : 'Pause background motion');
      applyHeroPlayState();
    });

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (en) { hero.visible = en[0].isIntersecting; applyHeroPlayState(); }, { threshold: 0.05 }).observe($('.hero'));
    }
    document.addEventListener('visibilitychange', applyHeroPlayState);
  }

  function buildImageSlide(slide, wrap, item) {
    var el = document.createElement('div');
    el.className = 'slide' + (item.fit === 'frame' ? ' slide--frame' : '');
    el.style.visibility = 'hidden';
    if (item.focus) el.style.setProperty('--focus', item.focus);
    if (item.fit === 'frame') {
      var bd = document.createElement('div');
      bd.className = 'frame-backdrop';
      bd.style.setProperty('--bg', 'url("' + absUrl(item.src) + '")');
      el.appendChild(bd);
    }
    var img = makeImg(item.src, '', { eager: slide.idx < 2 });
    img.className = 'slide-media';
    var box = document.createElement('div');
    box.className = 'frame-box';
    box.appendChild(img);
    el.appendChild(box);
    wrap.appendChild(el);
    var z = Number(item.zoom) || 1;
    slide.el = el; slide.media = img; slide.zoom = isPortraitPhone() ? Math.min(z, Number(item.zoomMobile) || 1.2) : z;
    var done = function () {
      if (img.naturalWidth) el.style.setProperty('--ar', img.naturalWidth + ' / ' + img.naturalHeight);
      if (motion) gsap.set(img, { scale: slide.zoom });
      else img.style.transform = 'scale(' + slide.zoom + ')';
      markReady(slide);
    };
    if (img.complete && img.naturalWidth) done();
    else { img.addEventListener('load', done, { once: true }); img.addEventListener('error', function () { el.remove(); }, { once: true }); }
  }

  function buildVideoSlide(slide, wrap, src) {
    var item = slide.item;
    var el = document.createElement('div');
    el.className = 'slide slide--video';
    el.style.visibility = 'hidden';
    if (item.focus) el.style.setProperty('--focus', item.focus);
    var v = document.createElement('video');
    v.className = 'slide-media';
    v.muted = true; v.defaultMuted = true; v.playsInline = true; v.loop = false;
    v.setAttribute('muted', ''); v.setAttribute('playsinline', ''); v.setAttribute('webkit-playsinline', '');
    v.setAttribute('disablepictureinpicture', '');
    v.preload = hero.slides.filter(function (s) { return s.video; }).length === 0 ? 'auto' : 'metadata';
    if (item.poster) v.poster = item.poster;
    v.src = src;
    el.appendChild(v);
    wrap.appendChild(el);
    slide.el = el; slide.media = v; slide.video = v; slide.zoom = 1;
    v.addEventListener('loadeddata', function () { markReady(slide); }, { once: true });
    v.addEventListener('error', function () { slide.ready = false; el.remove(); }, { once: true });
    v.load();
  }

  function readySlides() { return hero.slides.filter(function (s) { return s.ready; }); }

  function markReady(slide) {
    slide.ready = true;
    var total = readySlides().length;
    var t = $('[data-hero-total]'); if (t) t.textContent = String(total).padStart(2, '0');
    if (!hero.started && heroCanStart) startHero();
  }

  var heroCanStart = false;
  function releaseHero() { heroCanStart = true; if (readySlides().length) startHero(); }

  function startHero() {
    if (hero.started) return;
    hero.started = true;
    // Prefer the first slide in config order that is ready.
    showSlide(readySlides()[0], true);
  }

  function nextSlide() {
    var ready = readySlides();
    if (!ready.length) return;
    var curIdx = hero.current;
    var next = ready.filter(function (s) { return s.idx > curIdx; })[0] || ready[0];
    if (next.idx === curIdx && ready.length === 1) { scheduleNext(next); return; }
    showSlide(next, false);
  }

  function showSlide(slide, first) {
    var prev = hero.slides.filter(function (s) { return s.idx === hero.current; })[0];
    hero.current = slide.idx;
    var el = slide.el, m = slide.media, item = slide.item;
    var order = readySlides().indexOf(slide) + 1;
    var idxEl = $('[data-hero-index]'); if (idxEl) idxEl.textContent = String(order).padStart(2, '0');
    var cap = $('[data-hero-caption]'); if (cap) cap.textContent = item.caption || '';

    $$('.slide', el.parentNode).forEach(function (s) { s.style.zIndex = s === el ? 3 : (prev && s === prev.el ? 2 : 1); });
    el.style.visibility = 'visible';

    if (slide.video) { try { slide.video.currentTime = 0; } catch (e) { /* not seekable yet */ } playSafe(slide.video); }
    // Warm up the next video so the cut is seamless.
    var ready = readySlides();
    var upcoming = ready.filter(function (s) { return s.idx > slide.idx; })[0] || ready[0];
    if (upcoming && upcoming.video && upcoming.video.preload !== 'auto') upcoming.video.preload = 'auto';

    var dur = Math.max(2.5, Number(item.duration) || 6);

    if (motion) {
      gsap.killTweensOf([el, m]);
      if (first) {
        gsap.fromTo(el, { opacity: 0 }, { opacity: 1, duration: 1.2, ease: 'power2.out' });
      } else {
        gsap.fromTo(el, { clipPath: 'inset(100% 0% 0% 0%)', opacity: 1 }, { clipPath: 'inset(0% 0% 0% 0%)', duration: 1.25, ease: 'expo.inOut', clearProps: 'clipPath' });
        if (prev && prev.media) gsap.to(prev.media, { yPercent: -8, duration: 1.25, ease: 'expo.inOut' });
      }
      // Camera move
      var z = slide.zoom || 1;
      var mv = item.motion || (slide.video ? 'none' : 'push');
      gsap.set(m, { yPercent: 0 });
      if (mv === 'drift') gsap.fromTo(m, { scale: z * 1.04, xPercent: 2 }, { scale: z * 1.1, xPercent: -2, duration: dur + 1.4, ease: 'none' });
      else if (mv === 'push') gsap.fromTo(m, { scale: z * 1.12 }, { scale: z, duration: dur + 1.4, ease: 'power1.out' });
      else gsap.fromTo(m, { scale: 1.06 }, { scale: 1, duration: 1.6, ease: 'expo.out' });

      var bar = $('[data-hero-progress]');
      if (bar) { gsap.killTweensOf(bar); hero.barTween = gsap.fromTo(bar, { scaleX: 0 }, { scaleX: 1, duration: dur, ease: 'none' }); }
      if (prev && prev !== slide) {
        gsap.delayedCall(1.3, function () {
          if (hero.current !== prev.idx) { prev.el.style.visibility = 'hidden'; if (prev.video) prev.video.pause(); }
        });
      }
    } else {
      if (prev && prev !== slide) { prev.el.style.visibility = 'hidden'; if (prev.video) prev.video.pause(); }
    }
    scheduleNext(slide, dur);
    applyHeroPlayState();
  }

  function scheduleNext(slide, dur) {
    if (!motion) return; // reduced motion: no automatic cycling
    if (hero.timer) hero.timer.kill();
    hero.timer = gsap.delayedCall(dur || Number(slide.item.duration) || 6, nextSlide);
  }

  function playSafe(v) { var p = v.play(); if (p && p.catch) p.catch(function () { /* poster remains */ }); }

  function applyHeroPlayState() {
    var run = !hero.paused && hero.visible && !document.hidden;
    var cur = hero.slides.filter(function (s) { return s.idx === hero.current; })[0];
    if (hero.timer) hero.timer.paused(!run);
    if (hero.barTween) hero.barTween.paused(!run);
    if (cur && cur.video) { if (run) playSafe(cur.video); else cur.video.pause(); }
    if (motion && cur) gsap.getTweensOf(cur.media).forEach(function (t) { t.paused(!run); });
  }

  /* 06 · Intro brand reveal ---------------------------------------------- */
  function runIntro(done) {
    var intro = $('.intro');
    if (!motion || !root.classList.contains('intro-pending') || !intro) {
      root.classList.remove('intro-pending');
      done();
      return;
    }
    sSet('bg_intro_seen', '1');
    intro.classList.add('is-on');
    var txt = $('.intro-text', intro);
    var started = false;
    var play = function () {
      if (started) return;
      started = true;
      var tl = gsap.timeline({
        onComplete: function () { intro.remove(); root.classList.remove('intro-pending'); }
      });
      tl.to(txt, { strokeDashoffset: 0, duration: .9, ease: 'power2.inOut' })
        .to(txt, { fillOpacity: 1, duration: .3, ease: 'power1.out' }, '-=.35')
        .to(intro, { clipPath: 'inset(0% 0% 100% 0%)', duration: .8, ease: 'expo.inOut' }, '+=.05')
        .add(done, '-=.75');
    };
    // Draw with the real typeface, but never wait more than 600 ms for it.
    if (document.fonts && document.fonts.load) document.fonts.load('900 100px Archivo').then(play, play);
    setTimeout(play, 600);
  }

  function buildFlashStills(wrap) {
    if (!wrap) return [];
    var cfg = (MEDIA.heroIntro && MEDIA.heroIntro.stills) || [];
    var mobile = isPortraitPhone();
    return cfg.filter(function (s) { return s && s.src; }).slice(0, 4).map(function (s) {
      var el = document.createElement('div');
      el.className = 'flash-still';
      if (s.focus) el.style.setProperty('--focus', s.focus);
      var img = makeImg(s.src, '', { eager: true });
      img.addEventListener('error', function () { el.remove(); }, { once: true });
      el.appendChild(img);
      wrap.appendChild(el);
      el.__zoom = mobile ? Math.min(Number(s.zoom) || 1, 1.25) : (Number(s.zoom) || 1);
      return el;
    });
  }

  function heroEntrance() {
    if (!motion) { releaseHero(); return; }
    var stage = $('.hero-stage');
    var flashWrap = $('[data-hero-flash]');
    var stills = buildFlashStills(flashWrap);
    var step = .34;
    var land = .1 + stills.length * step + .15;
    var tl = gsap.timeline();
    stills.forEach(function (el, i) {
      var at = .1 + i * step;
      tl.to(el, { clipPath: 'inset(0% 0% 0% 0%)', duration: .7, ease: 'expo.inOut' }, at)
        .fromTo($('img', el), { scale: el.__zoom * 1.3 }, { scale: el.__zoom, duration: 1.5, ease: 'expo.out' }, at);
    });
    tl.add(releaseHero, Math.max(0, land - .45));
    if (stills.length) {
      tl.to(flashWrap, { clipPath: 'inset(0% 0% 100% 0%)', duration: 1, ease: 'expo.inOut' }, land + .25)
        .add(function () { flashWrap.innerHTML = ''; flashWrap.style.clipPath = ''; }, land + 1.3);
    }
    var from = stage ? getComputedStyle(stage).clipPath : 'none';
    if (stage && from && from !== 'none') {
      tl.fromTo(stage, { clipPath: from }, { clipPath: 'inset(0% 0% 0% 0%)', duration: 1.6, ease: 'expo.inOut',
        onComplete: function () { stage.style.clipPath = 'none'; } }, Math.max(.3, land - .25));
    }
    tl.fromTo('.hero-title .line > span', { yPercent: 140, y: 0 }, { yPercent: 0, duration: 1.25, ease: 'expo.out', stagger: .1 }, land)
      .fromTo('[data-hero-in]', { opacity: 0, y: 24 }, { opacity: 1, y: 0, duration: 1, ease: 'expo.out', stagger: .08, clearProps: 'transform' }, land + .35)
      .fromTo('.hero-base img', { scale: 1.08 }, { scale: 1, duration: 2.4, ease: 'expo.out' }, 0);
  }

  /* Find Your Grit step 1, answered inside the opening */
  function initHeroQuick() {
    var sel = $('[data-hero-goal]'), next = $('[data-hero-next]');
    if (!sel || !next) return;
    var sync = function () { next.disabled = !sel.value; };
    sel.addEventListener('change', sync);
    sync();
    next.addEventListener('click', function () {
      if (!sel.value) { sel.focus(); return; }
      track('assessment_start', { source: 'hero' });
      if (quizApi.startWith) quizApi.startWith({ goal: sel.value });
      scrollToEl($('#find-your-grit'));
    });
  }

  /* 07 · Smooth scroll & navigation -------------------------------------- */
  function initScroll() {
    if (motion && window.Lenis && mq('(pointer: fine)')) {
      lenis = new window.Lenis({ lerp: 0.1, smoothWheel: true, wheelMultiplier: 1 });
      lenis.on('scroll', ST.update);
      gsap.ticker.add(function (t) { lenis.raf(t * 1000); });
      gsap.ticker.lagSmoothing(0);
    }
    document.addEventListener('click', function (e) {
      var a = e.target.closest('a[href^="#"]');
      if (!a) return;
      var id = a.getAttribute('href');
      if (id.length < 2) return;
      var target = document.getElementById(id.slice(1));
      if (!target) return;
      e.preventDefault();
      closeMenu(true);
      scrollToEl(target);
    });
  }

  function scrollToEl(target) {
    var focusIt = function () {
      if (!target.hasAttribute('tabindex')) target.setAttribute('tabindex', '-1');
      target.focus({ preventScroll: true });
    };
    // Lazy media above the target can settle mid-scroll; re-check and correct.
    var settle = function (expected, tries) {
      var d = target.getBoundingClientRect().top - expected;
      if (Math.abs(d) > 6 && tries > 0) {
        if (lenis) lenis.scrollTo(target, { immediate: true, force: true });
        else window.scrollBy(0, d);
        setTimeout(function () { settle(expected, tries - 1); }, 250);
      } else focusIt();
    };
    if (lenis) {
      lenis.scrollTo(target, { offset: 0, duration: 1.4, easing: function (t) { return t === 1 ? 1 : 1 - Math.pow(2, -10 * t); }, onComplete: function () { settle(0, 3); } });
      return;
    }
    var pad = parseFloat(getComputedStyle(root).scrollPaddingTop) || 0;
    target.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    var done = false;
    var after = function () { if (done) return; done = true; settle(pad, 4); };
    if ('onscrollend' in window) window.addEventListener('scrollend', after, { once: true });
    setTimeout(after, reduceMotion ? 50 : 1200);
  }

  function initNav() {
    var nav = $('[data-nav]');
    var dock = $('[data-dock]');
    var heroEl = $('.hero');
    var applyEl = $('#apply');
    var last = 0, applyVisible = false;
    if ('IntersectionObserver' in window && applyEl) {
      new IntersectionObserver(function (en) { applyVisible = en[0].isIntersecting; onScroll(); }, { threshold: 0.05 }).observe(applyEl);
    }
    function onScroll() {
      var y = window.scrollY || window.pageYOffset;
      var heroH = heroEl ? heroEl.offsetHeight : 600;
      nav.classList.toggle('is-solid', y > heroH - 90);
      var menuOpen = root.classList.contains('menu-open');
      nav.classList.toggle('is-hidden', !menuOpen && y > 240 && y > last + 2);
      if (y < last - 2) nav.classList.remove('is-hidden');
      last = y;
      if (dock) dock.classList.toggle('is-visible', y > heroH * 0.7 && !applyVisible && !menuOpen);
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();

    // Active section highlight
    if (navIO) $$('.nav-links a').forEach(function (a) {
      var sec = document.getElementById(a.getAttribute('href').slice(1));
      if (sec) navIO.observe(sec);
    });
  }
  var navIO = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      $$('.nav-links a[href="#' + en.target.id + '"]').forEach(function (a) { a.classList.toggle('is-active', en.isIntersecting); });
    });
  }, { rootMargin: '-45% 0px -50% 0px' }) : null;

  /* Menu overlay */
  var menu, toggle, lastFocus;
  function initMenu() {
    menu = $('[data-menu]');
    toggle = $('[data-menu-toggle]');
    if (!menu || !toggle) return;
    toggle.addEventListener('click', function () { root.classList.contains('menu-open') ? closeMenu() : openMenu(); });
    document.addEventListener('keydown', function (e) {
      if (!root.classList.contains('menu-open')) return;
      if (e.key === 'Escape') { closeMenu(); toggle.focus(); }
      if (e.key === 'Tab') {
        var f = [toggle].concat($$('a, button', menu));
        var i = f.indexOf(document.activeElement);
        if (e.shiftKey && (i <= 0)) { e.preventDefault(); f[f.length - 1].focus(); }
        else if (!e.shiftKey && i === f.length - 1) { e.preventDefault(); f[0].focus(); }
      }
    });
  }
  function openMenu() {
    lastFocus = document.activeElement;
    menu.hidden = false;
    root.classList.add('menu-open');
    toggle.setAttribute('aria-expanded', 'true');
    $('.menu-toggle-label').textContent = 'Close';
    $('[data-nav]').classList.remove('is-hidden');
    if (lenis) lenis.stop();
    else document.body.style.overflow = 'hidden';
    var dock = $('[data-dock]'); if (dock) dock.classList.remove('is-visible');
    if (motion) {
      gsap.fromTo(menu, { clipPath: 'inset(0% 0% 100% 0%)' }, { clipPath: 'inset(0% 0% 0% 0%)', duration: .85, ease: 'expo.inOut' });
      gsap.fromTo($$('.menu-list a', menu), { yPercent: 100 }, { yPercent: 0, duration: 1, ease: 'expo.out', stagger: .05, delay: .35 });
    } else menu.style.clipPath = 'none';
    var first = $('.menu-list a', menu); if (first) setTimeout(function () { first.focus({ preventScroll: true }); }, 50);
  }
  function closeMenu(instant) {
    if (!menu || !root.classList.contains('menu-open')) return;
    root.classList.remove('menu-open');
    toggle.setAttribute('aria-expanded', 'false');
    $('.menu-toggle-label').textContent = 'Menu';
    if (lenis) lenis.start();
    else document.body.style.overflow = '';
    var hide = function () { menu.hidden = true; };
    if (motion && !instant) gsap.to(menu, { clipPath: 'inset(0% 0% 100% 0%)', duration: .7, ease: 'expo.inOut', onComplete: hide });
    else hide();
    if (lastFocus && !instant && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  }

  /* 08 · Motion ----------------------------------------------------------- */
  function splitWords(el) {
    var walk = function (node) {
      $$(':scope > *', node).forEach(walk);
      Array.prototype.slice.call(node.childNodes).forEach(function (n) {
        if (n.nodeType !== 3 || !n.textContent.trim()) return;
        var frag = document.createDocumentFragment();
        n.textContent.split(/(\s+)/).forEach(function (part) {
          if (!part) return;
          if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(part)); return; }
          var s = document.createElement('span'); s.className = 'w'; s.textContent = part; frag.appendChild(s);
        });
        node.replaceChild(frag, n);
      });
    };
    walk(el);
    return $$('.w', el);
  }

  function initMotion() {
    if (!motion) return;

    // Chapter copy and founder roles fade up
    $$('.chapter').forEach(function (c) {
      $$(':scope > :not(.chapter-m)', c).forEach(function (n) { n.setAttribute('data-reveal', 'up'); });
    });

    ST.batch('[data-reveal="up"]', {
      start: 'top 90%', once: true,
      onEnter: function (batch) { gsap.to(batch, { opacity: 1, y: 0, duration: 1.1, ease: 'expo.out', stagger: .08, overwrite: true }); }
    });

    $$('[data-reveal="mask"]').forEach(function (el) {
      var inner = $('.media', el) || (el.classList.contains('media') ? null : null);
      var tl = gsap.timeline({ scrollTrigger: { trigger: el, start: 'top 86%', once: true } });
      tl.to(el, { clipPath: 'inset(0% 0% 0% 0%)', duration: 1.5, ease: 'expo.inOut' });
      if (inner) tl.fromTo(inner, { scale: 1.3 }, { scale: 1, duration: 1.9, ease: 'expo.out' }, 0);
    });

    $$('[data-split-chars]').forEach(function (el) {
      el.setAttribute('aria-label', el.textContent.replace(/\s+/g, ' ').trim());
      var chars = [];
      $$('.line > span', el).forEach(function (span) {
        span.setAttribute('aria-hidden', 'true');
        var text = span.textContent;
        span.textContent = '';
        Array.prototype.forEach.call(text, function (c) {
          if (c === ' ') { span.appendChild(document.createTextNode(' ')); return; }
          var ch = document.createElement('span'); ch.className = 'ch'; ch.textContent = c; span.appendChild(ch); chars.push(ch);
        });
      });
      gsap.fromTo(chars, { yPercent: 112, y: 0, rotate: 7 }, {
        yPercent: 0, rotate: 0, duration: 1.15, ease: 'expo.out', stagger: .035,
        scrollTrigger: { trigger: el, start: 'top 86%', once: true }
      });
    });

    $$('[data-split-lines]').forEach(function (el) {
      gsap.fromTo($$('.line > span', el), { yPercent: 140, y: 0 }, {
        yPercent: 0, duration: 1.25, ease: 'expo.out', stagger: .09,
        scrollTrigger: { trigger: el, start: 'top 86%', once: true }
      });
    });

    $$('[data-parallax]').forEach(function (el) {
      var f = parseFloat(el.getAttribute('data-parallax')) || 0.12;
      var trigger = el.closest('section') || el.parentElement;
      gsap.fromTo(el, { yPercent: -f * 50 }, { yPercent: f * 50, ease: 'none', scrollTrigger: { trigger: trigger, start: 'top bottom', end: 'bottom top', scrub: true } });
    });

    // Philosophy statement: words light up as you read
    var st = $('[data-scrub-words]');
    if (st) {
      var words = splitWords(st);
      gsap.to(words, { opacity: 1, ease: 'none', stagger: .1, scrollTrigger: { trigger: st, start: 'top 82%', end: 'bottom 50%', scrub: .6 } });
    }

    // Editorial scale on the training film
    var pf = $('.philo-film .media');
    if (pf) gsap.fromTo(pf, { scale: 1.14 }, { scale: 1, ease: 'none', scrollTrigger: { trigger: pf, start: 'top bottom', end: 'bottom top', scrub: true } });

    // Kinetic band type
    var L = $('[data-band-left]'), R = $('[data-band-right]');
    if (L && R) {
      var bt = { trigger: '.band', start: 'top bottom', end: 'bottom top', scrub: true };
      gsap.fromTo(L, { xPercent: -30 }, { xPercent: 20, ease: 'none', scrollTrigger: bt });
      gsap.fromTo(R, { xPercent: 25 }, { xPercent: -12, ease: 'none', scrollTrigger: Object.assign({}, bt) });
    }

    // Footer wordmark rises
    gsap.fromTo('.footer-mark', { yPercent: 30, opacity: 0 }, { yPercent: 0, opacity: 1, ease: 'expo.out', duration: 1.6, scrollTrigger: { trigger: '.footer', start: 'top 85%', once: true } });

    initMarquee();
  }

  function initMarquee() {
    var track = $('[data-marquee]');
    if (!track) return;
    track.innerHTML += track.innerHTML; // seamless loop
    var tw = gsap.to(track, { xPercent: -50, duration: 34, ease: 'none', repeat: -1 });
    tw.totalTime(tw.duration() * 200); // headroom so it can also run backwards
    ST.create({
      trigger: track, start: 'top bottom', end: 'bottom top',
      onUpdate: function (self) {
        var dir = self.direction || 1;
        var v = Math.min(Math.abs(self.getVelocity()) / 300, 5);
        gsap.to(tw, { timeScale: dir * (1 + v), duration: .2, overwrite: true, onComplete: function () { gsap.to(tw, { timeScale: dir, duration: 1.2, ease: 'power2.out' }); } });
      }
    });
  }

  /* 09 · Founder ------------------------------------------------------------ */
  function initChapters() {
    var imgs = $$('[data-chapter-img]');
    var num = $('[data-chapter-num]');
    var chapters = $$('[data-chapter]');
    if (!('IntersectionObserver' in window) || !chapters.length) return;
    var setActive = function (i) {
      imgs.forEach(function (im) {
        var j = Number(im.getAttribute('data-chapter-img'));
        im.classList.toggle('is-active', j === i);
        im.classList.toggle('is-past', j < i);
      });
      if (num) num.textContent = String(i + 1).padStart(2, '0');
    };
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) setActive(Number(en.target.getAttribute('data-chapter'))); });
    }, { rootMargin: '-45% 0px -45% 0px' });
    chapters.forEach(function (c) { io.observe(c); });
    imgs.forEach(function (im) { im.style.transition = reduceMotion ? 'none' : 'clip-path 1.1s cubic-bezier(.76,0,.24,1)'; });
  }

  function initFilm() {
    var fig = $('[data-film]');
    var frame = $('[data-film-frame]');
    var item = MEDIA.interview;
    if (!fig || !frame) return;
    var heroFilm = $('[data-hero-film]');
    var showHeroFilm = function (playable) {
      if (!heroFilm) return;
      heroFilm.hidden = false;
      heroFilm.addEventListener('click', function () {
        scrollToEl(fig);
        if (playable) setTimeout(function () { var b = $('.film-play', frame); if (b) b.click(); }, reduceMotion ? 100 : 1500);
      });
    };
    if (!item || !item.src) { if (!placeholders) fig.hidden = true; else frame.innerHTML = placeholderHTML('interview', item || {}, false); return; }
    var embed = toEmbed(item.src);
    var posterLib = LIB[item.fallbackPoster];

    var setPoster = function () {
      probe(item.poster).then(function (ok) {
        var src = ok === false || !item.poster ? (posterLib && posterLib.src) : item.poster;
        if (!src) return;
        var img = makeImg(src, '', {});
        img.style.objectPosition = '50% 30%';
        frame.appendChild(img);
      });
    };

    var addPlay = function (onPlay) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'film-play';
      b.setAttribute('aria-label', 'Play the founder film: ' + (item.title || 'In his words'));
      b.innerHTML = '<span>Play film</span>';
      b.addEventListener('click', function () { track('film_play', { film: 'founder' }); onPlay(); b.remove(); });
      frame.insertBefore(b, frame.firstChild);
    };

    if (embed) {
      showHeroFilm(true);
      setPoster();
      addPlay(function () {
        frame.innerHTML = '<iframe src="' + esc(embed) + '" title="' + esc(item.title || 'Founder film') + '" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe>';
      });
      return;
    }
    probe(item.src).then(function (ok) {
      if (ok === false) {
        if (!placeholders) { fig.hidden = true; return; }
        showHeroFilm(false);
        setPoster();
        frame.insertAdjacentHTML('beforeend', placeholderHTML('interview', item, true));
        return;
      }
      showHeroFilm(true);
      setPoster();
      addPlay(function () {
        var v = document.createElement('video');
        v.controls = true; v.playsInline = true; v.setAttribute('playsinline', '');
        if (item.poster) v.poster = item.poster;
        v.src = item.src;
        frame.innerHTML = '';
        frame.appendChild(v);
        playSafe(v);
        if (hero.timer) { hero.paused = true; applyHeroPlayState(); }
      });
    });
  }

  function toEmbed(url) {
    var m = /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/shorts\/|youtube\.com\/embed\/)([\w-]{6,})/.exec(url || '');
    if (m) return 'https://www.youtube-nocookie.com/embed/' + m[1] + '?autoplay=1&rel=0&modestbranding=1&playsinline=1';
    m = /vimeo\.com\/(?:video\/)?(\d+)/.exec(url || '');
    if (m) return 'https://player.vimeo.com/video/' + m[1] + '?autoplay=1&title=0&byline=0&portrait=0';
    return null;
  }

  /* 10 · Coaching -------------------------------------------------------- */
  function initOffers() {
    var offers = $$('.offer');
    var canHover = mq('(hover: hover) and (min-width: 1000px)');
    offers.forEach(function (o) {
      var link = $('.offer-link', o);
      link.addEventListener('click', function () {
        var f = link.getAttribute('data-format');
        if (f) setFormat(f);
      });
      if (!canHover) return;
      var m = $('.offer-media', o);
      var qx = motion ? gsap.quickTo(m, 'x', { duration: .7, ease: 'power3' }) : null;
      var qr = motion ? gsap.quickTo(m, 'rotation', { duration: .9, ease: 'power3' }) : null;
      if (motion) gsap.set(m, { yPercent: -50 });
      var lastX = 0;
      o.addEventListener('mouseenter', function (e) { o.classList.add('is-hover'); move(e, true); });
      o.addEventListener('mouseleave', function () { o.classList.remove('is-hover'); if (qr) qr(0); });
      o.addEventListener('mousemove', move);
      function move(e, jump) {
        var r = o.getBoundingClientRect();
        var w = m.offsetWidth;
        var x = Math.min(Math.max(e.clientX - r.left - w / 2, 0), r.width - w);
        if (!qx) { m.style.transform = 'translate(' + x + 'px,-50%)'; return; }
        if (jump === true) gsap.set(m, { x: x });
        else qx(x);
        qr(Math.max(-6, Math.min(6, (e.clientX - lastX) * .25)));
        lastX = e.clientX;
      }
    });
    if (SITE.pricingNote) { var p = $('[data-pricing-note]'); p.textContent = SITE.pricingNote; p.hidden = false; }
  }

  /* 11 · Find Your Grit assessment ---------------------------------------
     Question structure preserved from the original GritFit concept.       */
  var QUESTIONS = [
    { key: 'goal', title: "What's your primary fitness goal?", sub: 'Pick the outcome that matters most right now.', answers: ['Build muscle & strength', 'Lose body fat', 'Improve athletic performance', 'Get fit and stay consistent'] },
    { key: 'mode', title: 'How do you want to train?', sub: 'Big Grit coaches in person and online.', answers: ['In person — Dallas–Fort Worth', 'Online coaching', 'Open to either'] },
    { key: 'challenge', title: "What's been getting in the way?", sub: 'The right plan solves the real obstacle.', answers: ['Staying consistent', 'Knowing what to do', 'Making time', 'Accountability'] },
    { key: 'timeline', title: 'When do you want to start?', sub: 'No pressure — this just helps prioritize your next step.', answers: ['Within 30 days', 'In the next 1–3 months', 'Just exploring for now'] },
    { key: 'ready', title: 'How do you feel about personal coaching?', sub: 'This is about readiness, not an obligation to buy.', answers: ['Ready to discuss investment', 'Interested, need more details', 'Exploring options first'] }
  ];
  var CHALLENGE_LINE = {
    'Staying consistent': 'The plan is built to make showing up the default — structure first, motivation second.',
    'Knowing what to do': 'You get a clear plan, so every session has a purpose and nothing is guesswork.',
    'Making time': 'Your training is built around the time you actually have — not the time you wish you had.',
    'Accountability': 'You get a coach who notices — and expects you back.'
  };
  var answers = {};
  var qIndex = 0;
  var quizApi = {};

  function priority() {
    if (!answers.timeline || !answers.ready) return '';
    if (answers.timeline === 'Within 30 days' && answers.ready === 'Ready to discuss investment') return 'High intent';
    if (answers.timeline === 'Just exploring for now' || answers.ready === 'Exploring options first') return 'Longer-term nurture';
    return 'Follow-up priority';
  }
  function recommendation() {
    if (answers.mode === 'In person — Dallas–Fort Worth') return { title: 'Personal Training — DFW', text: 'In-person, one-on-one coaching with Delone in Dallas–Fort Worth — built around your goal and your schedule.' };
    if (answers.mode === 'Online coaching') return { title: 'Online Coaching', text: 'Online coaching gives you the structure, programming and accountability of Big Grit — wherever you train.' };
    return { title: "Let's find your fit", text: 'In-person and online coaching could both work for you. Your consultation will identify the better path for your goal and routine.' };
  }

  function initQuiz() {
    var box = $('[data-quiz]');
    if (!box) return;
    var qEl = $('[data-quiz-q]', box), rEl = $('[data-quiz-result]', box), sEl = $('[data-quiz-start]', box);
    var opts = $('[data-quiz-options]', box), back = $('[data-quiz-back]', box);

    try { var saved = JSON.parse(sGet('bg_assessment') || 'null'); if (saved && typeof saved === 'object') answers = saved; } catch (e) { answers = {}; }

    $('[data-quiz-begin]', box).addEventListener('click', function () {
      track('assessment_start');
      answers = {}; qIndex = 0;
      sEl.hidden = true; qEl.hidden = false;
      render(true);
    });
    back.addEventListener('click', function () { if (qIndex > 0) { qIndex--; render(true); } });
    quizApi.startWith = function (pre) {
      answers = Object.assign({}, pre || {});
      qIndex = Math.min(Object.keys(answers).length, QUESTIONS.length - 1);
      sEl.hidden = true; rEl.hidden = true; qEl.hidden = false;
      render(false);
    };
    $('[data-quiz-restart]', box).addEventListener('click', function () {
      answers = {}; qIndex = 0; sDel('bg_assessment');
      rEl.hidden = true; qEl.hidden = false; render(true);
    });
    $('[data-result-continue]', box).addEventListener('click', function () { applyAssessmentToForm(); });

    document.addEventListener('keydown', function (e) {
      if (qEl.hidden || e.metaKey || e.ctrlKey || e.altKey) return;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test((e.target || {}).tagName)) return;
      var r = box.getBoundingClientRect();
      if (r.bottom < 0 || r.top > window.innerHeight) return;
      var n = parseInt(e.key, 10);
      var q = QUESTIONS[qIndex];
      if (n >= 1 && n <= q.answers.length) { e.preventDefault(); choose(q.answers[n - 1]); }
    });

    function render(focus) {
      var q = QUESTIONS[qIndex];
      $('[data-quiz-step]', box).textContent = String(qIndex + 1).padStart(2, '0');
      var bar = $('[data-quiz-bar]', box); bar.style.transform = 'scaleX(' + ((qIndex + 1) / QUESTIONS.length) + ')';
      var title = $('[data-quiz-title]', box);
      title.textContent = q.title;
      $('[data-quiz-sub]', box).textContent = q.sub;
      opts.innerHTML = q.answers.map(function (a, i) {
        var on = answers[q.key] === a;
        return '<button type="button" class="opt-btn" data-answer="' + esc(a) + '" aria-pressed="' + on + '">' +
          '<span class="opt-key" aria-hidden="true">' + (i + 1) + '</span><span>' + esc(a) + '</span>' +
          '<svg class="opt-tick" viewBox="0 0 18 10" aria-hidden="true"><path d="M1 5l4 4 8-8" fill="none" stroke="currentColor" stroke-width="1.6"/></svg></button>';
      }).join('');
      back.disabled = qIndex === 0;
      $$('.opt-btn', opts).forEach(function (b) { b.addEventListener('click', function () { choose(b.getAttribute('data-answer')); }); });
      if (motion) gsap.fromTo([title, $('[data-quiz-sub]', box)].concat($$('.opt-btn', opts)), { opacity: 0, y: 26 }, { opacity: 1, y: 0, duration: .7, ease: 'expo.out', stagger: .045 });
      if (focus) title.focus({ preventScroll: true });
    }

    var advancing = false;
    function choose(a) {
      if (advancing) return;
      var q = QUESTIONS[qIndex];
      answers[q.key] = a;
      $$('.opt-btn', opts).forEach(function (b) { b.setAttribute('aria-pressed', String(b.getAttribute('data-answer') === a)); });
      advancing = true;
      setTimeout(function () {
        advancing = false;
        if (qIndex < QUESTIONS.length - 1) {
          var go = function () { qIndex++; render(true); };
          if (motion) gsap.to($$('.opt-btn', opts).concat([$('[data-quiz-title]', box)]), { opacity: 0, y: -16, duration: .28, ease: 'power2.in', stagger: .02, onComplete: go });
          else go();
        } else showResult();
      }, reduceMotion ? 120 : 380);
    }

    function showResult() {
      sSet('bg_assessment', JSON.stringify(answers));
      var rec = recommendation();
      $('[data-result-title]', box).textContent = rec.title;
      $('[data-result-text]', box).textContent = rec.text + ' ' + (CHALLENGE_LINE[answers.challenge] || '');
      var rows = [['Goal', answers.goal], ['Format', answers.mode], ['Obstacle', answers.challenge], ['Start', answers.timeline]];
      $('[data-result-profile]', box).innerHTML = rows.map(function (r) { return '<div><dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1] || '—') + '</dd></div>'; }).join('');
      qEl.hidden = true; rEl.hidden = false;
      track('assessment_complete', { recommendation: rec.title, goal: answers.goal, format: answers.mode, timeline: answers.timeline });
      applyAssessmentToForm(true);
      if (motion) gsap.fromTo($$(':scope > *', rEl), { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: .9, ease: 'expo.out', stagger: .07 });
      $('[data-result-title]', box).focus({ preventScroll: true });
    }

    // Returning visitor who already finished: keep the result available.
    if (answers && answers.ready && answers.mode) {
      sEl.hidden = true; qIndex = QUESTIONS.length - 1; showResultSilently();
    }
    function showResultSilently() {
      var rec = recommendation();
      $('[data-result-title]', box).textContent = rec.title;
      $('[data-result-text]', box).textContent = rec.text + ' ' + (CHALLENGE_LINE[answers.challenge] || '');
      var rows = [['Goal', answers.goal], ['Format', answers.mode], ['Obstacle', answers.challenge], ['Start', answers.timeline]];
      $('[data-result-profile]', box).innerHTML = rows.map(function (r) { return '<div><dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1] || '—') + '</dd></div>'; }).join('');
      rEl.hidden = false;
      applyAssessmentToForm(true);
    }
  }

  function setFormat(value) {
    $$('input[name="format"]').forEach(function (r) { r.checked = r.value === value; });
  }
  function setSelect(name, value) {
    var s = $('select[name="' + name + '"]');
    if (!s || !value) return;
    $$('option', s).forEach(function (o) { if (o.textContent === value) s.value = o.value || o.textContent; });
  }
  function setHidden(name, value) { var i = $('input[name="' + name + '"]'); if (i) i.value = value || ''; }

  function applyAssessmentToForm(quiet) {
    if (!answers || !answers.mode) return;
    var fmt = answers.mode === 'Open to either' ? 'Not sure yet' : answers.mode;
    if (!quiet || !$('input[name="format"]:checked')) setFormat(fmt);
    setSelect('goal', answers.goal);
    setSelect('timeline', answers.timeline);
    var rec = recommendation();
    setHidden('assessment_challenge', answers.challenge);
    setHidden('assessment_readiness', answers.ready);
    setHidden('recommendation', rec.title);
    setHidden('lead_priority', priority());
    var box = $('[data-form-assessment]');
    if (box) {
      box.innerHTML = 'Find Your Grit attached: <b>' + esc(rec.title) + '</b> · ' + esc(answers.goal || '') + (answers.timeline ? ' · ' + esc(answers.timeline) : '');
      box.hidden = false;
    }
  }

  /* 12 · Results & products --------------------------------------------- */
  function initResults() {
    var section = $('[data-results]');
    var trackEl = $('[data-results-track]');
    if (!section || !trackEl) return;
    var list = (MEDIA.transformations || []).filter(function (t) { return t && (t.before || t.after); });
    var quotes = (SITE.testimonials || []).filter(function (q) { return q && q.quote; });
    var shown = 0;
    var footage = $('[data-slot="clientFootage"]', section);

    var update = function () {
      var footageOk = footage && /asset|placeholder/.test(footage.getAttribute('data-state') || '');
      var any = shown > 0 || quotes.length > 0 || footageOk || placeholders;
      section.hidden = !any;
      $$('[data-nav-results]').forEach(function (n) { n.hidden = !any; });
      if (any) refreshSoon();
    };
    if (footage) footage.__onSlot = update;

    list.forEach(function (t, i) {
      Promise.all([probe(t.before), probe(t.after)]).then(function (r) {
        var ok = r[0] !== false && r[1] !== false;
        if (!ok && !placeholders) return;
        var fig = document.createElement('figure');
        fig.className = 'tcard';
        if (ok) {
          fig.innerHTML = '<div class="cmp">' +
            '<img src="' + esc(t.before) + '" alt="' + esc((t.name || 'Client') + ' before') + '" loading="lazy" decoding="async">' +
            '<img class="cmp-after" src="' + esc(t.after) + '" alt="' + esc((t.name || 'Client') + ' after') + '" loading="lazy" decoding="async">' +
            '<span class="cmp-tag cmp-tag--b">Before</span><span class="cmp-tag cmp-tag--a">After</span><span class="cmp-line"></span>' +
            '<input type="range" min="0" max="100" value="50" aria-label="Compare before and after for ' + esc(t.name || 'client ' + (i + 1)) + '"></div>' +
            '<figcaption><b>' + esc(t.name || '') + '</b><span>' + esc(t.caption || '') + '</span></figcaption>';
          var cmp = $('.cmp', fig), range = $('input', fig);
          range.addEventListener('input', function () { cmp.style.setProperty('--x', range.value + '%'); });
        } else {
          fig.innerHTML = '<div class="cmp media">' + placeholderHTML('transformations.' + i, { src: t.before + '  +  ' + t.after, spec: t.spec }, false) + '</div>' +
            '<figcaption><b>Client ' + (i + 1) + '</b><span>Awaiting real, approved photos</span></figcaption>';
        }
        trackEl.appendChild(fig);
        shown++;
        update();
      });
    });

    var qEl = $('[data-quotes]');
    if (quotes.length) {
      qEl.innerHTML = quotes.map(function (q) {
        return '<blockquote class="quote" data-reveal="up"><p>“' + esc(q.quote) + '”</p><footer><b>' + esc(q.name || '') + '</b>' + esc(q.detail || '') + '</footer></blockquote>';
      }).join('');
    } else if (placeholders) {
      qEl.innerHTML = '<blockquote class="quote"><p style="color:var(--dim)">“Testimonial slot — add real client words in config/site.config.js.”</p><footer><b>Client name</b>Coaching type · duration</footer></blockquote>';
    }
    update();
    dragScroll($('[data-results-rail]'));
  }

  function dragScroll(el) {
    if (!el || !mq('(pointer: fine)')) return;
    var down = false, startX = 0, startL = 0, moved = false;
    el.addEventListener('pointerdown', function (e) {
      if (e.pointerType !== 'mouse' || e.target.closest('input')) return;
      down = true; moved = false; startX = e.clientX; startL = el.scrollLeft; el.classList.add('is-drag');
    });
    window.addEventListener('pointermove', function (e) {
      if (!down) return;
      var dx = e.clientX - startX;
      if (Math.abs(dx) > 3) moved = true;
      el.scrollLeft = startL - dx;
    });
    window.addEventListener('pointerup', function () { if (down) { down = false; el.classList.remove('is-drag'); } });
    el.addEventListener('click', function (e) { if (moved) { e.preventDefault(); e.stopPropagation(); } }, true);
  }

  function initProducts() {
    var section = $('[data-playbooks]');
    var grid = $('[data-products]');
    if (!section || !grid) return;
    var products = (SITE.products || []).filter(function (p) { return p && p.title && isSafeUrl(p.checkoutUrl); });
    if (!products.length && !placeholders) return;
    if (products.length) {
      grid.innerHTML = products.map(function (p) {
        return '<article class="product" data-reveal="up">' +
          '<div class="media">' + (p.image ? '<img src="' + esc(p.image) + '" alt="' + esc(p.title) + ' cover" loading="lazy" decoding="async">' : '') + '</div>' +
          '<p class="product-format">' + esc(p.format || '') + '</p><h3>' + esc(p.title) + '</h3>' +
          (p.description ? '<p>' + esc(p.description) + '</p>' : '') +
          '<div class="product-buy"><span class="product-price">' + esc(p.price || '') + '</span>' +
          '<a class="btn btn--grit btn--sm" href="' + esc(p.checkoutUrl) + '" target="_blank" rel="noopener" data-track="product_click" data-cta="' + esc(p.title) + '"><span>' + esc(p.ctaLabel || 'Buy now') + '</span><i class="arrow" aria-hidden="true"></i></a></div></article>';
      }).join('');
    } else {
      grid.innerHTML = '<article class="product"><div class="media">' + placeholderHTML('products', { src: 'media/products/your-guide-cover.jpg', spec: 'No digital products were found in the original site. Add guides in config/site.config.js → products (title, price, cover 1200×1500, real checkout link). This section stays hidden until you do.' }, false) + '</div>' +
        '<p class="product-format">Product slot</p><h3>Your playbook</h3><div class="product-buy"><span class="product-price">$—</span></div></article>';
    }
    section.hidden = false;
    $$('[data-nav-products]').forEach(function (n) { n.hidden = false; });
  }

  /* 13 · Application form ------------------------------------------------ */
  function initAttribution() {
    var first = null;
    try { first = JSON.parse(sGet('bg_first_touch') || 'null'); } catch (e) { first = null; }
    if (!first) {
      var p = new URLSearchParams(location.search);
      first = {
        utm_source: p.get('utm_source') || '', utm_medium: p.get('utm_medium') || '', utm_campaign: p.get('utm_campaign') || '',
        referrer: document.referrer && document.referrer.indexOf(location.host) === -1 ? document.referrer : '',
        landing_page: location.pathname + location.search
      };
      sSet('bg_first_touch', JSON.stringify(first));
    }
    Object.keys(first).forEach(function (k) { setHidden(k, String(first[k]).slice(0, 300)); });
  }

  function initForm() {
    var form = $('[data-apply-form]');
    if (!form) return;
    var status = $('[data-form-status]', form);
    var btn = $('[data-submit]', form);
    var provider = ((SITE.forms || {}).provider || 'none').toLowerCase();

    if (SITE.bookingUrl && isSafeUrl(SITE.bookingUrl)) {
      var bd = $('[data-book-direct]'); bd.hidden = false; $('[data-booking-link]', bd).href = SITE.bookingUrl;
    }

    var rules = {
      name: function (v) { return v.trim().length >= 2 ? '' : 'Please enter your name.'; },
      email: function (v) { return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()) ? '' : 'Please enter a valid email address.'; },
      format: function () { return $('input[name="format"]:checked', form) ? '' : 'Choose how you want to train.'; },
      goal: function (v) { return v ? '' : 'Choose your primary goal.'; },
      consent: function () { return $('input[name="consent"]', form).checked ? '' : 'Please confirm we may contact you about coaching.'; }
    };
    function check(name) {
      var input = form.elements[name];
      var el = input && input.length && !input.tagName ? input[0] : input;
      var msg = rules[name](el && el.type !== 'radio' && el.type !== 'checkbox' ? el.value : '');
      var err = $('#e-' + name, form);
      if (err) err.textContent = msg;
      if (el && el.type !== 'radio') el.setAttribute('aria-invalid', msg ? 'true' : 'false');
      return msg ? (name === 'format' ? $('input[name="format"]', form) : el) : null;
    }
    ['name', 'email', 'goal'].forEach(function (n) {
      form.elements[n].addEventListener('blur', function () { if (form.elements[n].value) check(n); });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      status.textContent = ''; status.classList.remove('is-error');
      var invalid = Object.keys(rules).map(check).filter(Boolean);
      if (invalid.length) { invalid[0].focus(); status.textContent = 'Please fix the highlighted fields.'; status.classList.add('is-error'); return; }

      var data = new FormData(form);
      if ((data.get('bg_hp_field') || '').trim()) { showDone(data); return; } // bot trap

      btn.setAttribute('aria-busy', 'true');
      $('span', btn).textContent = 'Sending…';
      send(provider, data).then(function () {
        track('generate_lead', { format: data.get('format'), goal: data.get('goal'), priority: data.get('lead_priority') || '' });
        sDel('bg_assessment');
        showDone(data);
      }).catch(function (err) {
        var fallback = SITE.contactEmail ? ' You can also email <a href="mailto:' + esc(SITE.contactEmail) + '?subject=' + encodeURIComponent('Coaching application') + '">' + esc(SITE.contactEmail) + '</a>.' : '';
        status.innerHTML = esc(err && err.userMessage ? err.userMessage : "We couldn't send your application. Please check your connection and try again.") + fallback;
        status.classList.add('is-error');
        if (window.console) console.warn('[Big Grit] Form submission failed:', err);
      }).then(function () {
        btn.removeAttribute('aria-busy');
        $('span', btn).textContent = 'Send application';
      });
    });

    function showDone(data) {
      form.hidden = true;
      var done = $('[data-apply-done]');
      done.hidden = false;
      var book = $('[data-done-book]', done);
      if (SITE.bookingUrl && isSafeUrl(SITE.bookingUrl)) {
        var u;
        try {
          u = new URL(SITE.bookingUrl, location.href);
          if (/calendly\.com|cal\.com/.test(u.hostname)) { u.searchParams.set('name', data.get('name') || ''); u.searchParams.set('email', data.get('email') || ''); }
          book.href = u.toString();
        } catch (e) { book.href = SITE.bookingUrl; }
        book.hidden = false;
        $('[data-done-text]', done).textContent = 'Your application is with Delone. Lock in your consultation now — pick a time that works for you.';
      }
      done.focus({ preventScroll: true });
      if (motion) gsap.fromTo($$(':scope > *', done), { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: .9, ease: 'expo.out', stagger: .08 });
      refreshSoon();
    }
  }

  function send(provider, data) {
    var ctl = 'AbortController' in window ? new AbortController() : null;
    var timer = ctl ? setTimeout(function () { ctl.abort(); }, 15000) : null;
    var opts = function (o) { if (ctl) o.signal = ctl.signal; return o; };
    var fail = function (msg) { var e = new Error(msg); e.userMessage = msg; throw e; };
    var p;
    var cfg = SITE.forms || {};
    if (provider === 'netlify') {
      p = fetch('/', opts({ method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(data).toString() }));
    } else if (provider === 'formspree' && cfg.formspreeEndpoint) {
      p = fetch(cfg.formspreeEndpoint, opts({ method: 'POST', headers: { Accept: 'application/json' }, body: data }));
    } else if (provider === 'webhook' && cfg.webhookUrl) {
      var body = new URLSearchParams(data);
      body.delete('form-name'); body.delete('bg_hp_field');
      body.append('submitted_at', new Date().toISOString());
      p = fetch(cfg.webhookUrl, opts({ method: 'POST', body: body }));
    } else {
      return Promise.reject({ userMessage: 'Online applications are not connected yet.' + (SITE.contactEmail ? '' : ' Please reach out on social media for now.') });
    }
    return p.then(function (r) {
      if (timer) clearTimeout(timer);
      if (!r.ok) fail("We couldn't send your application (error " + r.status + '). Please try again in a moment.');
      return r;
    }, function (err) {
      if (timer) clearTimeout(timer);
      if (err && err.name === 'AbortError') fail('Sending took too long. Please check your connection and try again.');
      throw err;
    });
  }

  /* Socials & footer --------------------------------------------------- */
  function initContact() {
    var links = [];
    if (isSafeUrl(SITE.instagramUrl) && SITE.instagramUrl) links.push(['Instagram', SITE.instagramUrl]);
    if (isSafeUrl(SITE.youtubeUrl) && SITE.youtubeUrl) links.push(['YouTube', SITE.youtubeUrl]);
    if (isSafeUrl(SITE.tiktokUrl) && SITE.tiktokUrl) links.push(['TikTok', SITE.tiktokUrl]);
    var html = links.map(function (l) { return '<a href="' + esc(l[1]) + '" target="_blank" rel="noopener">' + esc(l[0]) + '</a>'; }).join('');
    $$('[data-socials]').forEach(function (el) { el.innerHTML = html; });
    var fc = $('[data-footer-contact]');
    if (fc) {
      if (SITE.contactEmail) fc.insertAdjacentHTML('beforeend', '<li><a href="mailto:' + esc(SITE.contactEmail) + '">' + esc(SITE.contactEmail) + '</a></li>');
      links.forEach(function (l) { fc.insertAdjacentHTML('beforeend', '<li><a href="' + esc(l[1]) + '" target="_blank" rel="noopener">' + esc(l[0]) + '</a></li>'); });
    }
    $$('[data-year]').forEach(function (el) { el.textContent = new Date().getFullYear(); });
  }

  /* 14 · Boot ------------------------------------------------------------- */
  function boot() {
    loadAnalytics();
    initContact();
    initMenu();
    initScroll();
    initNav();
    initSlots();
    initHero();
    initFilm();
    initChapters();
    initOffers();
    initQuiz();
    initHeroQuick();
    initResults();
    initProducts();
    initAttribution();
    initForm();
    initMotion();
    runIntro(heroEntrance);
    if (motion) {
      if (document.fonts && document.fonts.ready) document.fonts.ready.then(refreshSoon);
      window.addEventListener('load', refreshSoon);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
