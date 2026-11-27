// Hiệu ứng giao diện mới (v2): header thu gọn, thanh cuộn, menu mobile, hiện dần, đếm số, ảnh lệch theo chuột.
(function () {
  'use strict';
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var header = document.getElementById('siteHeader');
  var bar = document.getElementById('scrollBar');
  function onScroll() {
    var max = document.documentElement.scrollHeight - window.innerHeight;
    if (bar) bar.style.setProperty('--p', max > 0 ? Math.min(window.scrollY / max, 1) : 0);
    if (header) header.classList.toggle('scrolled', window.scrollY > 24);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  var toggle = document.getElementById('menuToggle');
  var nav = document.getElementById('main-nav');
  if (toggle && nav) {
    toggle.addEventListener('click', function () {
      var open = nav.classList.toggle('open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    nav.addEventListener('click', function () { nav.classList.remove('open'); toggle.setAttribute('aria-expanded', 'false'); });
  }
  document.addEventListener('click', function (e) {
    document.querySelectorAll('.acc-menu[open]').forEach(function (d) { if (!d.contains(e.target)) d.removeAttribute('open'); });
  });


  // Nút số lượng (trang chi tiết món)
  document.querySelectorAll('[data-qty-step]').forEach(function (b) {
    b.addEventListener('click', function () {
      var input = b.parentElement.querySelector('[data-qty-input]');
      if (!input) return;
      var v = parseInt(input.value || '1', 10) + parseInt(b.dataset.qtyStep, 10);
      input.value = Math.max(1, Math.min(99, isNaN(v) ? 1 : v));
    });
  });

  // Nút "Thêm vào giỏ": chưa nối backend nên chỉ báo nhẹ. Khi có chức năng giỏ hàng, bỏ [data-add-to-cart] và gắn xử lý thật.
  var toast;
  document.querySelectorAll('[data-add-to-cart]').forEach(function (b) {
    b.addEventListener('click', function () {
      if (!toast) {
        toast = document.createElement('div');
        toast.className = 'v2-toast';
        toast.setAttribute('role', 'status');
        document.body.appendChild(toast);
      }
      toast.textContent = b.dataset.msg || 'Chức năng giỏ hàng đang được phát triển.';
      toast.classList.add('show');
      clearTimeout(toast._t);
      toast._t = setTimeout(function () { toast.classList.remove('show'); }, 2600);
    });
  });

  // Đếm số
  function fmt(el, n) {
    var d = parseInt(el.dataset.decimals || '0', 10);
    el.textContent = n.toFixed(d).replace('.', ',') + (el.dataset.suffix || '');
  }
  function runCount(el) {
    var to = parseFloat(el.dataset.count), t0 = performance.now(), dur = 1600;
    (function tick(t) {
      var p = Math.min((t - t0) / dur, 1);
      fmt(el, to * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(tick);
    })(t0);
  }

  var items = document.querySelectorAll('[data-reveal]');
  var counts = document.querySelectorAll('[data-count]');
  if (reduced || !('IntersectionObserver' in window)) {
    counts.forEach(function (el) { fmt(el, parseFloat(el.dataset.count)); });
    items.forEach(function (el) { el.classList.add('reveal', 'in'); });
  } else {
    counts.forEach(function (el) { fmt(el, 0); });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        var el = e.target;
        el.classList.add('in');
        el.querySelectorAll('[data-count]').forEach(runCount);
        io.unobserve(el);
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -8% 0px' });
    items.forEach(function (el) {
      el.classList.add('reveal');
      if (el.dataset.revealDir) el.classList.add('reveal-' + el.dataset.revealDir);
      if (el.dataset.reveal) el.style.setProperty('--d', el.dataset.reveal + 'ms');
      io.observe(el);
    });
  }

  // Ảnh hero lệch theo chuột (mượt bằng nội suy)
  var art = document.querySelector('[data-tilt]');
  if (!reduced && art && window.matchMedia('(hover: hover)').matches) {
    var tx = 0, ty = 0, cx = 0, cy = 0, raf = 0, rect = null;
    var loop = function () {
      cx += (tx - cx) * 0.12; cy += (ty - cy) * 0.12;
      art.style.setProperty('--mx', cx.toFixed(3));
      art.style.setProperty('--my', cy.toFixed(3));
      raf = (Math.abs(tx - cx) > 0.002 || Math.abs(ty - cy) > 0.002) ? requestAnimationFrame(loop) : 0;
    };
    var kick = function () { if (!raf) raf = requestAnimationFrame(loop); };
    art.addEventListener('pointerenter', function () { rect = art.getBoundingClientRect(); });
    art.addEventListener('pointermove', function (e) {
      rect = rect || art.getBoundingClientRect();
      tx = ((e.clientX - rect.left) / rect.width - 0.5) * 2;
      ty = ((e.clientY - rect.top) / rect.height - 0.5) * 2;
      kick();
    }, { passive: true });
    art.addEventListener('pointerleave', function () { tx = 0; ty = 0; rect = null; kick(); });
  }
})();
