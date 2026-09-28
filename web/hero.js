// The hero story: a heartbeat calms down and folds into a crescent moon as the page scrolls. That is the app in
// one picture: the heart rate drops, sleep begins, and only then does the countdown start.
// The line (#pulse) morphs twice: to a calmer beat, then to the moon. The beat marker (#beat) becomes a star.
(function () {
  var root = document.documentElement;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!window.gsap || !window.ScrollTrigger || !window.MorphSVGPlugin) return; // CDN blocked: the still heartbeat stays
  gsap.registerPlugin(ScrollTrigger, MorphSVGPlugin);
  root.classList.add('story-on');

  // A reload starts the story again from the top (a link to a section still goes where it points).
  ScrollTrigger.clearScrollMemory('manual');
  if (!location.hash) window.scrollTo(0, 0);
  window.addEventListener('beforeunload', function () { window.scrollTo(0, 0); });

  var calm = document.getElementById('pulseCalm').getAttribute('d');
  var moon = document.getElementById('moon').getAttribute('d');
  var pulse = document.getElementById('pulse');
  var length = pulse.getTotalLength();

  function finalState() {
    gsap.set(pulse, { morphSVG: moon, fillOpacity: 1, strokeWidth: 0 });
    gsap.set('#beat', { attr: { cx: 470, cy: 150, r: 12 } });
    gsap.set('#stars circle', { opacity: 1 });
    gsap.set(['.story-grid', '#line1'], { opacity: 0 });
    gsap.set('#line2', { opacity: 1, y: 0 });
  }

  if (reduce) {
    root.classList.add('story-still');
    finalState();
    return;
  }

  gsap.set('.story-mark', { xPercent: -50, yPercent: -50, left: '50%', top: '50%' });

  // 1. On arrival: the heartbeat draws itself, and its marker pops in like a beat.
  gsap.set(pulse, { strokeDasharray: length, strokeDashoffset: length });
  gsap.timeline({ defaults: { ease: 'power3.out' } })
    .to(pulse, { strokeDashoffset: 0, duration: 1.6, ease: 'power2.inOut' })
    .from('#beat', { scale: 0, transformOrigin: '50% 50%', duration: 0.6, ease: 'back.out(3)' }, '-=0.3')
    .from('.story-words', { opacity: 0, y: 24, duration: 0.8 }, '-=0.5')
    .set(pulse, { strokeDasharray: 'none' });

  // 2. On scroll (scrubbed, so it follows the finger both ways): the beat calms, then becomes the moon.
  var story = gsap.timeline({
    defaults: { ease: 'power2.inOut' },
    scrollTrigger: {
      trigger: '.story', start: 'top top', end: 'bottom bottom', scrub: 1,
      onUpdate: function (self) { idle(self.progress > 0.97); },
    },
  });
  story
    .to('#line1', { opacity: 0, y: -30, duration: 0.12 }, 0.04)
    .to(pulse, { morphSVG: calm, duration: 0.18 }, 0.06)
    .to('.story-grid', { opacity: 0, duration: 0.16 }, 0.2)
    .to(pulse, { morphSVG: { shape: moon, shapeIndex: 'auto' }, fillOpacity: 1, strokeWidth: 0, duration: 0.36 }, 0.26)
    .to('#beat', { attr: { cx: 470, cy: 150, r: 12 }, duration: 0.34 }, 0.28)
    .to('#stars circle', { opacity: 1, duration: 0.2, stagger: 0.04 }, 0.5)
    .fromTo('#line2', { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: 0.14 }, 0.52);
  story.to('.story-mark', { scale: 0.92, duration: 0.2 }, 0.8).to({}, { duration: 0.05 }, 0.95);

  // 3. Resting at the end, the night stays alive: the stars twinkle one after another.
  var idleTl = null;
  function idle(on) {
    if (on && !idleTl) {
      idleTl = gsap.timeline({ repeat: -1, delay: 0.8 })
        .to('#stars circle, #beat', { opacity: 0.35, duration: 0.9, ease: 'sine.inOut', yoyo: true, repeat: 1, stagger: { each: 0.5 } });
    } else if (!on && idleTl) {
      idleTl.kill();
      idleTl = null;
      gsap.set('#stars circle, #beat', { opacity: 1 });
    }
  }
})();

// The rest of the page: a quiet reveal — each block rises a little and fades in once, cards one after another.
(function () {
  if (!window.gsap || !window.ScrollTrigger) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  var items = document.querySelectorAll(
    '.hero-grid > div > *, main section:not(.story) h2, .card, .checks li, .steps li, .rules, .download > *, details, .en p');
  gsap.set(items, { opacity: 0, y: 28 });
  ScrollTrigger.batch(items, {
    start: 'top 88%',
    once: true,
    onEnter: function (batch) {
      gsap.to(batch, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out', stagger: 0.08, overwrite: true });
    },
  });
})();

// Screenshots in the three app languages.
(function () {
  var screens = [
    ['01-setup', 'مدة النوم'], ['05-waiting', 'بانتظار النوم'], ['07-sunrise', 'منبّه الشروق'], ['08-summary', 'ملخّص الصباح'],
    ['02-duration', 'اختيار المدة'], ['06-alarm', 'وقت الاستيقاظ'], ['03-history', 'آخر الليالي'], ['04-settings', 'الإعدادات'],
  ];
  var gallery = document.getElementById('gallery');
  if (!gallery) return;
  function render(lang) {
    gallery.innerHTML = '';
    screens.forEach(function (s) {
      var fig = document.createElement('figure');
      var img = document.createElement('img');
      img.src = 'docs/screenshots/' + lang + '/' + s[0] + '.png';
      img.alt = s[1];
      img.loading = 'lazy';
      img.width = 360; img.height = 360;
      var cap = document.createElement('figcaption');
      cap.textContent = s[1];
      fig.appendChild(img); fig.appendChild(cap);
      gallery.appendChild(fig);
    });
    document.querySelectorAll('[data-gallery]').forEach(function (b) {
      b.setAttribute('aria-selected', String(b.dataset.gallery === lang));
    });
  }
  document.querySelectorAll('[data-gallery]').forEach(function (b) {
    b.addEventListener('click', function () { render(b.dataset.gallery); });
  });
  render('ar');
})();
