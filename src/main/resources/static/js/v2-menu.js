// Lọc / tìm / sắp xếp món ở trang thực đơn v2 (chạy phía trình duyệt trên danh sách hiện có).
(function () {
  'use strict';
  var grid = document.getElementById('menuGrid');
  if (!grid) return;
  var cards = Array.prototype.slice.call(grid.children);
  var original = cards.slice();
  var q = document.getElementById('q');
  var sort = document.getElementById('sort');
  var count = document.getElementById('resultCount');
  var empty = document.getElementById('menuEmpty');
  var tabs = document.querySelectorAll('.cat-list .tab');
  var cat = '';

  function apply() {
    var kw = (q.value || '').trim().toLowerCase();
    var list = original.filter(function (el) {
      return (!cat || el.dataset.cat === cat) && (!kw || el.dataset.name.indexOf(kw) !== -1);
    });
    if (sort.value === 'price_asc') list.sort(function (a, b) { return a.dataset.price - b.dataset.price; });
    if (sort.value === 'price_desc') list.sort(function (a, b) { return b.dataset.price - a.dataset.price; });
    original.forEach(function (el) { el.hidden = true; });
    list.forEach(function (el) { el.hidden = false; grid.appendChild(el); });
    count.textContent = list.length + ' món';
    empty.hidden = list.length > 0;
  }

  tabs.forEach(function (t) {
    t.addEventListener('click', function () {
      tabs.forEach(function (x) { x.classList.remove('on'); });
      t.classList.add('on');
      cat = t.dataset.filter || '';
      apply();
    });
  });
  q.addEventListener('input', apply);
  sort.addEventListener('change', apply);
})();
