// Hiệu ứng cho các trang khách Thymeleaf cũ: chỉ thêm class, không đụng HTML/logic của trang.
(function () {
  'use strict';
  var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;


  // Ảnh món lấy từ Unsplash (chạy local nên dùng link ngoài); lỗi mạng thì tự quay về ảnh trong /images.
  var photo = function (id) { return 'https://images.unsplash.com/photo-' + id + '?auto=format&fit=crop&w=900&q=75'; };
  var photos = {
    'chicken-bowl.jpg': '1546069901-ba9599a7e63c',
    'chicken-salad.jpg': '1540189549336-e6e99c3679fe',
    'salmon-bowl.jpg': '1467003909585-2f8a72700288',
    'beef-bowl.jpg': '1490645935967-10de6ba17061',
    'green-bowl.jpg': '1512621776951-a57141f2eefd',
    'quinoa-salad.jpg': '1512621776951-a57141f2eefd',
    'seafood-bowl.jpg': '1569718212165-3a8278d5f624',
    'hero-bowl.jpg': '1540189549336-e6e99c3679fe',
    'welcome-green.jpg': '1490645935967-10de6ba17061'
  };
  document.querySelectorAll('img[src^="/images/"]').forEach(function (img) {
    var file = img.getAttribute('src').split('/').pop();
    if (!photos[file]) return;
    var local = img.getAttribute('src');
    img.addEventListener('error', function () { img.src = local; }, { once: true });
    img.src = photo(photos[file]);
  });

  // Dải chữ chạy dưới hero (chỉ trang chủ)
  var hero = document.querySelector('.hero-section');
  if (hero) {
    var words = ['Cơm healthy', 'Salad tươi', 'Món cao cấp', 'Nấu mới mỗi ngày', 'Ít dầu mỡ', 'Rõ dinh dưỡng', 'Giao tận nơi', 'Eat clean'];
    var strip = document.createElement('div');
    strip.className = 'st-marquee';
    strip.setAttribute('aria-hidden', 'true');
    var track = document.createElement('div');
    track.className = 'st-marquee-track';
    words.concat(words).forEach(function (w) {
      var span = document.createElement('span');
      span.textContent = w;
      track.appendChild(span);
    });
    strip.appendChild(track);
    hero.insertAdjacentElement('afterend', strip);
  }

  // Thanh tiến độ cuộn + đổ bóng header
  var bar = document.createElement('div');
  bar.className = 'st-scroll-bar';
  bar.setAttribute('aria-hidden', 'true');
  document.body.appendChild(bar);
  var header = document.querySelector('.site-header');
  function onScroll() {
    var max = document.documentElement.scrollHeight - window.innerHeight;
    bar.style.setProperty('--p', max > 0 ? Math.min(window.scrollY / max, 1) : 0);
    if (header) header.classList.toggle('scrolled', window.scrollY > 24);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // Hiện dần khi cuộn tới
  var targets = document.querySelectorAll(
    '.section-head, .food-card, .category-strip, .green-promo, .section-block > *, .auth-card, .cart-item, .summary-card'
  );
  if (reduced || !('IntersectionObserver' in window)) return;
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      e.target.classList.add('st-in');
      io.unobserve(e.target);
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
  var seen = new Map();
  targets.forEach(function (el) {
    var group = el.parentElement;
    var i = seen.get(group) || 0;
    seen.set(group, i + 1);
    el.style.setProperty('--d', Math.min(i, 5) * 110 + 'ms');
    el.classList.add('st-reveal');
    io.observe(el);
  });
})();
