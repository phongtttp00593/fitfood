
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

    if (cls) {
      el.className = cls;
    }

    if (content !== undefined) {
      el.textContent = String(content);
    }

    return el;
  }

  function checkedInputs(groupId) {
    if (!variantBody) return [];

    return Array.from(
      variantBody.querySelectorAll('input[data-group-id]')
    ).filter(function (el) {
      return el.dataset.groupId === String(groupId) && el.checked;
    });
  }

  function getExtraPrice() {
    if (!variantBody) return 0;

    var extra = 0;

    variantBody.querySelectorAll('input:checked').forEach(function (input) {
      extra += Number(input.dataset.price || 0);
    });

    return extra;
  }

  function calculate() {
    var basePrice = Number(data.basePrice) || 0;
    var unit = Math.max(0, basePrice + getExtraPrice());

    var basePriceElement = document.getElementById('displayBasePrice');
    var totalElement = document.getElementById('modalTotalPrice');
    var quantityElement = document.getElementById('modalQty');

    if (basePriceElement) {
      basePriceElement.textContent = money(unit);
    }

    if (totalElement) {
      totalElement.textContent = money(unit * quantity);
    }

    if (quantityElement) {
      quantityElement.textContent = quantity;
    }

    var quantityInput = document.getElementById('quantityInput');

    if (quantityInput) {
      quantityInput.value = quantity;
    }
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
        return opt.is_available === true || opt.is_available === 1;
      });

      var wrapper = create('div');
      wrapper.style.marginBottom = '14px';

      var heading = create('div', '', group.group_name);
      heading.style.cssText =
        'font-weight:600;margin-bottom:7px;color:#334155';

      heading.appendChild(
        create(
          'small',
          '',
          group.is_required ? ' (Bắt buộc)' : ' (Tùy chọn)'
        )
      );

      wrapper.appendChild(heading);

      var defaults = options.filter(function (opt) {
        return opt.is_default === true || opt.is_default === 1;
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

        var isDefault =
          opt.is_default === true || opt.is_default === 1;

        input.checked = isDefault ||
          (!defaults.length && group.is_required && idx === 0);

        if (group.allow_multiple && idx >= max && input.checked) {
          input.checked = false;
        }

        input.addEventListener('change', function () {
          if (
            group.allow_multiple &&
            checkedInputs(group.id).length > max
          ) {
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
    calculate();
  };

  // Gửi sản phẩm tới CartController để lưu vào database.
  window.addToCartWithVariants = function () {
    if (!data.id) {
      alert('Không xác định được sản phẩm.');
      return;
    }

    // Kiểm tra các nhóm tùy chọn bắt buộc.
    for (var i = 0; i < groups.length; i++) {
      var group = groups[i];

      if (
        (group.is_required === true || group.is_required === 1) &&
        checkedInputs(group.id).length === 0
      ) {
        alert('Vui lòng chọn: ' + group.group_name);
        return;
      }
    }

    // Ưu tiên dùng form có sẵn trong trang nếu form gửi tới /cart/add.
    var form = document.querySelector('form[action*="/cart/add"]');

    if (!form) {
      form = document.createElement('form');
      form.method = 'POST';
      form.action = '/cart/add';
      form.style.display = 'none';
      document.body.appendChild(form);
    }

    function setHidden(name, value) {
      var input = form.querySelector('input[name="' + name + '"]');

      if (!input) {
        input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        form.appendChild(input);
      }

      input.value = String(value);
    }

    setHidden('productId', data.id);
    setHidden('quantity', quantity);

    // Nếu form chưa có CSRF token, lấy token có sẵn trên trang.
    var csrfInput = form.querySelector('input[name="_csrf"]');

    if (!csrfInput) {
      var pageCsrfInput = document.querySelector(
        'input[name="_csrf"]'
      );

      var csrfMeta = document.querySelector(
        'meta[name="_csrf"]'
      );

      var csrfHeaderMeta = document.querySelector(
        'meta[name="_csrf_header"]'
      );

      var csrfToken = pageCsrfInput
        ? pageCsrfInput.value
        : (csrfMeta ? csrfMeta.content : null);

      if (csrfToken) {
        setHidden('_csrf', csrfToken);
      } else if (csrfHeaderMeta) {
        console.warn(
          'Có thông tin CSRF header nhưng không tìm thấy token.'
        );
      }
    }

    // Không hiển thị thông báo thành công trước khi server xử lý.
    form.submit();
  };

  buildVariants();
})();
