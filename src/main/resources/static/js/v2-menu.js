// Lọc danh mục, tìm kiếm và sắp xếp món ăn trên trang /menu.
(function () {
  'use strict';

  function initMenu() {
    var grid = document.getElementById('menuGrid');
    if (!grid) return;

    var cards = Array.prototype.slice.call(grid.children);
    var q = document.getElementById('q');
    var sort = document.getElementById('sort');
    var count = document.getElementById('resultCount');
    var empty = document.getElementById('menuEmpty');
    var tabs = Array.prototype.slice.call(document.querySelectorAll('.cat-list .tab'));
    var cat = '';
    var initialCategory = new URLSearchParams(window.location.search).get('category');

    function normalize(value) {
      return (value || '').toString().trim().toLocaleLowerCase('vi-VN');
    }

    function apply() {
      var keyword = normalize(q ? q.value : '');
      var visible = cards.filter(function (card) {
        var cardCategory = normalize(card.getAttribute('data-cat'));
        var name = normalize(card.getAttribute('data-name'));
        var description = normalize(card.getAttribute('data-description'));
        var matchesCategory = !cat || cardCategory === normalize(cat);
        var matchesKeyword = !keyword || name.indexOf(keyword) !== -1 || description.indexOf(keyword) !== -1 || cardCategory.indexOf(keyword) !== -1;
        return matchesCategory && matchesKeyword;
      });

      if (sort && sort.value === 'price_asc') {
        visible.sort(function (a, b) { return Number(a.dataset.price || 0) - Number(b.dataset.price || 0); });
      } else if (sort && sort.value === 'price_desc') {
        visible.sort(function (a, b) { return Number(b.dataset.price || 0) - Number(a.dataset.price || 0); });
      }

      cards.forEach(function (card) { card.hidden = true; });
      visible.forEach(function (card) {
        card.hidden = false;
        grid.appendChild(card);
      });

      if (count) count.textContent = visible.length + ' món';
      if (empty) empty.hidden = visible.length !== 0;
    }

    tabs.forEach(function (tab) {
      tab.addEventListener('click', function () {
        cat = tab.getAttribute('data-filter') || '';
        tabs.forEach(function (other) {
          other.classList.remove('on', 'active');
          other.setAttribute('aria-pressed', 'false');
        });
        tab.classList.add('on', 'active');
        tab.setAttribute('aria-pressed', 'true');
        apply();
      });
    });

    if (q) q.addEventListener('input', apply);
    if (sort) sort.addEventListener('change', apply);

    if (initialCategory) {
      var requested = tabs.find(function (tab) {
        return normalize(tab.getAttribute('data-filter')) === normalize(initialCategory);
      });
      if (requested) requested.click();
      else apply();
    } else {
      apply();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMenu);
  } else {
    initMenu();
  }
})();
