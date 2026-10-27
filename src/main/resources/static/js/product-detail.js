
(function () {
  'use strict';

  var data = window.fitfoodProduct;
  if (!data) return;

  var quantity = 1;
  var variantBody = document.getElementById('userVariantBody');

  var money = function (value) {
    return new Intl.NumberFormat('vi-VN', {
      maximumFractionDigits: 0
    }).format(value) + 'đ';
  };

  var groups = Array.isArray(data.groups) ? data.groups : [];

  function create(tag, cls, content) {
    var el = document.createElement(tag);
    if (cls) el.className = cls;
    if (content !== undefined) {
      el.textContent = String(content);
    }
    return el;
  }

  function checkedInputs(groupId) {
    return Array.from(
      variantBody.querySelectorAll('input[data-group-id]')
    ).filter(function (el) {
      return el.dataset.groupId === String(groupId) && el.checked;
    });
  }

  function calculate() {
    var extra = 0;

    variantBody.querySelectorAll('input:checked').forEach(function (input) {
      extra += Number(input.dataset.price || 0);
    });

    var unit = Math.max(0, Number(data.basePrice) + extra);

    document.getElementById('displayBasePrice').textContent = money(unit);
    document.getElementById('modalTotalPrice').textContent =
      money(unit * quantity);
  }

  function buildVariants() {
    if (!variantBody) return;

    variantBody.replaceChildren();

    if (!groups.length) {
      variantBody.appendChild(
        create('p', '', 'Món ăn này chưa có tùy chọn bổ sung.')
      );
      calculate();
      return;
    }

    groups.forEach(function (group) {
      var options = (group.options || []).filter(function (opt) {
        return !!opt.is_available;
      });

      var wrapper = create('div');
      wrapper.style.marginBottom = '14px';

      var heading = create('div', '', group.group_name);
      heading.style.cssText =
        'font-weight:600;margin-bottom:7px;color:#334155';

      heading.appendChild(
        create('small', '',
          group.is_required ? ' (Bắt buộc)' : ' (Tùy chọn)')
      );

      wrapper.appendChild(heading);

      var defaults = options.filter(function (opt) {
        return !!opt.is_default;
      });

      var max = group.allow_multiple
        ? Math.max(1, Number(group.max_select) || 1)
        : 1;

      options.forEach(function (opt, idx) {
        var label = create('label');

        label.style.cssText =
          'display:flex;justify-content:space-between;gap:10px;' +
          'align-items:center;padding:9px;background:#fff;' +
          'border:1px solid #cbd5e1;border-radius:7px;' +
          'margin-bottom:6px;cursor:pointer';

        var left = create('span');
        left.style.cssText =
          'display:flex;align-items:center;gap:8px';

        var input = create('input');

        input.type = group.allow_multiple ? 'checkbox' : 'radio';
        input.name = 'variant_' + group.id;
        input.value = opt.id;
        input.dataset.groupId = String(group.id);
        input.dataset.price = String(opt.extra_price || 0);

        input.checked = !!opt.is_default ||
          (!defaults.length && !!group.is_required && idx === 0);

        if (group.allow_multiple && idx >= max && input.checked) {
          input.checked = false;
        }

        input.addEventListener('change', function () {
          if (group.allow_multiple &&
              checkedInputs(group.id).length > max) {

            input.checked = false;
            alert('Chỉ được chọn tối đa ' + max + ' tùy chọn.');
          }
          calculate();
        });

        left.appendChild(input);
        left.appendChild(create('span', '', opt.option_name));

        var price = Number(opt.extra_price || 0);
        var labelPrice = price === 0
          ? 'Miễn phí'
          : (price > 0 ? '+' : '-') + money(Math.abs(price));

        var right = create('strong', '', labelPrice);
        right.style.whiteSpace = 'nowrap';

        label.appendChild(left);
        label.appendChild(right);
        wrapper.appendChild(label);
      });

      if (!options.length) {
        wrapper.appendChild(
          create('p', '', 'Hiện chưa có tùy chọn khả dụng.')
        );
      }

      variantBody.appendChild(wrapper);
    });

    calculate();
  }

  window.changeQty = function (step) {
    quantity = Math.min(99, Math.max(1, quantity + Number(step)));

    document.getElementById('modalQty').textContent = quantity;

    calculate();
  };

  window.addToCartWithVariants = function () {
    for (var group of groups) {
      if (group.is_required && checkedInputs(group.id).length === 0) {
        alert('Vui lòng chọn: ' + group.group_name);
        return;
      }
    }

    alert(
      'Bạn đã chọn món và tùy chọn. ' +
      'Chức năng lưu vào giỏ hàng chưa được kết nối ở bước này.'
    );
  };

  buildVariants();
})();
