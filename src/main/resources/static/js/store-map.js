/* Bản đồ ở chân trang: Leaflet + OpenStreetMap (miễn phí, không cần khóa).
   - Ghim cửa hàng (toạ độ "vĩ độ,kinh độ" trong data-query, hoặc tìm theo địa chỉ bằng Nominatim).
   - Nút "Vị trí của tôi" xin quyền định vị của trình duyệt rồi hiện vị trí người xem + khoảng cách tới quán.
   Thư viện chỉ tải khi bản đồ sắp vào màn hình. */
(function () {
  'use strict';
  var el = document.getElementById('storeMap');
  if (!el) return;

  var btn = document.getElementById('storeMapLocate');
  var statusEl = document.getElementById('storeMapStatus');
  var dirLink = document.querySelector('.f-map-link');
  var address = el.getAttribute('data-address') || 'Hải Phòng, Việt Nam';
  var query = (el.getAttribute('data-query') || address).trim();
  var DEFAULT_CENTER = [20.8449, 106.6881]; // Hải Phòng
  var LEAFLET_CSS = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
  var LEAFLET_JS = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';

  var map, storeMarker, storePos, meMarker, meCircle, routeLine;
  var accuracyM = 0;
  var routeToken = 0;
  var started = false;

  function say(msg, isError) {
    if (!statusEl) return;
    statusEl.textContent = msg || '';
    statusEl.classList.toggle('is-error', !!isError);
  }

  function parseLatLng(text) {
    var m = /^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,; ]\s*(-?\d{1,3}(?:\.\d+)?)\s*$/.exec(text);
    if (!m) return null;
    var lat = parseFloat(m[1]);
    var lng = parseFloat(m[2]);
    return (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) ? [lat, lng] : null;
  }

  // Khoảng cách đường chim bay (haversine), đơn vị km
  function km(a, b) {
    var R = 6371;
    var rad = Math.PI / 180;
    var dLat = (b[0] - a[0]) * rad;
    var dLng = (b[1] - a[1]) * rad;
    var x = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.sqrt(x));
  }

  function esc(t) {
    var table = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' };
    return String(t).replace(/[&<>"]/g, function (c) { return table[c]; });
  }

  // Nút "Chỉ đường trên Google Maps" dùng đúng toạ độ đang ghim (không để Google tự đoán từ chữ)
  function updateDirLink() {
    if (!dirLink || !storePos) return;
    var url = 'https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=' +
              storePos[0].toFixed(6) + ',' + storePos[1].toFixed(6);
    if (meMarker) {
      var m = meMarker.getLatLng();
      url += '&origin=' + m.lat.toFixed(6) + ',' + m.lng.toFixed(6);
    }
    dirLink.href = url;
  }

  function setStore(pos) {
    storePos = pos;
    updateDirLink();
    if (storeMarker) {
      storeMarker.setLatLng(pos);
    } else {
      storeMarker = L.marker(pos, { title: 'FITFOOD' }).addTo(map)
        .bindPopup('<strong>FITFOOD</strong><br>' + esc(address));
    }
  }

  // Các cách tìm thu hẹp dần: đầy đủ -> bỏ mã lô đầu dòng -> chỉ phường/quận + thành phố
  function candidates() {
    var list = [query];
    var noLot = query.replace(/^\s*[A-Za-zĐđ]{1,3}\s?\d[\w.\-\/]*\s+/, '').trim();
    if (noLot && noLot !== query) list.push(noLot);
    var parts = query.split(',').map(function (p) { return p.trim(); }).filter(Boolean);
    if (parts.length > 2) list.push(parts.slice(-2).join(', '));
    return list;
  }

  // done(pos, level): level 0 = khớp đầy đủ, 1 = chỉ gần đúng (khu vực), -1 = không tìm thấy
  function geocodeStore(done) {
    var coords = parseLatLng(query);
    if (coords) { done(coords, 0); return; }
    var list = candidates();
    var i = 0;
    function next() {
      if (i >= list.length) { done(DEFAULT_CENTER, -1); return; }
      var level = i === 0 ? 0 : 1;
      var url = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=vn&accept-language=vi&q=' +
                encodeURIComponent(list[i]);
      i += 1;
      fetch(url).then(function (r) { return r.json(); }).then(function (rows) {
        if (rows && rows[0]) done([parseFloat(rows[0].lat), parseFloat(rows[0].lon)], level);
        else setTimeout(next, 1100); // Nominatim: tối đa 1 yêu cầu/giây
      }).catch(function () { setTimeout(next, 1100); });
    }
    next();
  }

  function showMe(latlng, accuracy) {
    var pos = [latlng.lat, latlng.lng];
    if (meMarker) {
      meMarker.setLatLng(pos);
      meCircle.setLatLng(pos).setRadius(accuracy);
    } else {
      meCircle = L.circle(pos, { radius: accuracy, color: '#2b6cff', weight: 1, fillOpacity: 0.12 }).addTo(map);
      meMarker = L.circleMarker(pos, { radius: 8, color: '#fff', weight: 3, fillColor: '#2b6cff', fillOpacity: 1 })
        .addTo(map).bindPopup('Vị trí của bạn');
    }
    accuracyM = accuracy;
    updateDirLink();
    if (storePos) map.fitBounds(L.latLngBounds([pos, storePos]).pad(0.25), { maxZoom: 17 });
    else map.setView(pos, 16);
    if (storePos) updateRoute();
    else say('Vị trí của bạn đã hiện trên bản đồ (chấm xanh). Độ chính xác ±' + Math.round(accuracy) + ' m.');
  }

  function fmtDist(meters) {
    return meters < 1000 ? Math.round(meters) + ' m' : (meters / 1000).toFixed(1) + ' km';
  }

  // Tính quãng đường đi bằng đường bộ (OSRM, miễn phí) và vẽ tuyến lên bản đồ
  function updateRoute() {
    if (!meMarker || !storePos) return;
    var a = meMarker.getLatLng();
    var token = ++routeToken;
    var acc = ' Độ chính xác vị trí ±' + Math.round(accuracyM) + ' m.';
    say('Đang tính đường đi…');
    var url = 'https://router.project-osrm.org/route/v1/driving/' +
              a.lng + ',' + a.lat + ';' + storePos[1] + ',' + storePos[0] +
              '?overview=full&geometries=geojson';
    fetch(url).then(function (r) { return r.json(); }).then(function (j) {
      if (token !== routeToken) return;
      if (!j || j.code !== 'Ok' || !j.routes || !j.routes[0]) throw new Error('no route');
      var rt = j.routes[0];
      if (routeLine) map.removeLayer(routeLine);
      routeLine = L.geoJSON(rt.geometry, { style: { color: '#2b6cff', weight: 5, opacity: 0.8 } }).addTo(map);
      map.fitBounds(routeLine.getBounds().pad(0.2), { maxZoom: 17 });
      var mins = Math.max(1, Math.round(rt.duration / 60));
      say('Đi đường bộ tới cửa hàng: ' + fmtDist(rt.distance) + ', khoảng ' + mins + ' phút (ước tính).' + acc);
    }).catch(function () {
      if (token !== routeToken) return;
      var d = km([a.lat, a.lng], storePos) * 1000;
      say('Không tính được đường đi. Khoảng cách thẳng tới cửa hàng khoảng ' + fmtDist(d) +
          ' (chỉ là ước lượng, đường đi thực tế dài hơn).' + acc, true);
    });
  }

  function locate() {
    if (!navigator.geolocation) { say('Trình duyệt không hỗ trợ định vị.', true); return; }
    say('Đang lấy vị trí của bạn…');
    if (btn) btn.disabled = true;
    navigator.geolocation.getCurrentPosition(function (p) {
      if (btn) btn.disabled = false;
      showMe({ lat: p.coords.latitude, lng: p.coords.longitude }, p.coords.accuracy || 50);
    }, function (err) {
      if (btn) btn.disabled = false;
      say(err && err.code === 1
        ? 'Bạn đã chặn quyền vị trí. Bấm biểu tượng khóa cạnh thanh địa chỉ, cho phép "Vị trí" rồi thử lại.'
        : 'Không lấy được vị trí (cần bật GPS hoặc Wi-Fi). Hãy thử lại.', true);
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 });
  }

  function init() {
    if (started) return;
    started = true;
    map = L.map(el, { scrollWheelZoom: false }).setView(DEFAULT_CENTER, 15);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
    }).addTo(map);
    // Bản đồ nằm trong trang cuộn: chỉ phóng to bằng lăn chuột sau khi bấm vào bản đồ
    map.on('click', function () { map.scrollWheelZoom.enable(); });
    map.on('mouseout', function () { map.scrollWheelZoom.disable(); });

    geocodeStore(function (pos, level) {
      setStore(pos);
      var zoom = level === 0 ? 17 : (level === 1 ? 15 : 13);
      if (meMarker) { map.fitBounds(L.latLngBounds([meMarker.getLatLng(), pos]).pad(0.25), { maxZoom: 17 }); updateRoute(); }
      else map.setView(pos, zoom);
      if (meMarker) { /* đã có đường đi, giữ thông báo đường đi */ }
      else if (level === 1) say('Ghim đang ở khu vực gần quán (chưa chính xác đến số nhà). Cần toạ độ để ghim đúng.', true);
      else if (level < 0) say('Chưa tìm thấy địa chỉ quán, đang hiện khu vực Hải Phòng. Cần toạ độ để ghim đúng.', true);
      // Nếu người dùng đã từng cho phép vị trí thì tự định vị luôn, không hỏi lại
      if (navigator.permissions && navigator.permissions.query) {
        navigator.permissions.query({ name: 'geolocation' }).then(function (r) {
          if (r.state === 'granted') locate();
        }).catch(function () {});
      }
    });
  }

  function loadLeaflet(after) {
    if (window.L) { init(); if (after) after(); return; }
    var css = document.createElement('link');
    css.rel = 'stylesheet';
    css.href = LEAFLET_CSS;
    document.head.appendChild(css);
    var js = document.createElement('script');
    js.src = LEAFLET_JS;
    js.async = true;
    js.onload = function () { init(); if (after) after(); };
    js.onerror = function () {
      el.textContent = 'Không tải được bản đồ (cần kết nối Internet). Hãy dùng nút "Chỉ đường trên Google Maps".';
      el.classList.add('is-error');
    };
    document.head.appendChild(js);
  }

  // Bấm "Vị trí của tôi": nếu bản đồ chưa tải thì tải rồi định vị
  if (btn) {
    btn.addEventListener('click', function () {
      if (window.L && map) locate();
      else loadLeaflet(locate);
    });
  }

  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      if (entries.some(function (e) { return e.isIntersecting; })) {
        io.disconnect();
        loadLeaflet();
      }
    }, { rootMargin: '300px' });
    io.observe(el);
  } else {
    loadLeaflet();
  }
})();
