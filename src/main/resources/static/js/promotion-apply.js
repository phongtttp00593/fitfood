
document.addEventListener("DOMContentLoaded", function () {

    const input = document.getElementById("promo-code");
    const applyBtn = document.getElementById("apply-promo");
    const removeBtn = document.getElementById("remove-promo");

    const message = document.getElementById("promo-message");
    const discountRow = document.getElementById("promo-discount-row");
    const discountText = document.getElementById("promo-discount");
    const appliedCode = document.getElementById("applied-promo-code");

    const subtotalText = document.getElementById("cart-subtotal");
    const shippingText = document.getElementById("cart-shipping");
    const totalText = document.getElementById("cart-total");

    const cartList = document.getElementById("cart-items-list");

    if (!input || !applyBtn || !removeBtn ||
        !message || !discountRow || !discountText ||
        !appliedCode || !subtotalText || !shippingText ||
        !totalText || !cartList) {
        return;
    }

    let originalTotal = 0;
    let cartLoaded = false;
    let requestVersion = 0;

    // ĐỊNH DẠNG TIỀN VIỆT NAM
    function formatMoney(amount) {
        return Number(amount).toLocaleString("vi-VN") + "đ";
    }

    // HIỂN THỊ THÔNG BÁO
    function showMessage(text, success) {

        message.textContent = text;
        message.style.display = "block";
        message.style.color = success
            ? "#15803d"
            : "#dc2626";
    }

    // HỦY KẾT QUẢ KHUYẾN MÃI ĐÃ ÁP DỤNG
    function resetPromotion() {

        requestVersion++;

        discountRow.style.display = "none";
        discountText.textContent = "-0đ";
        appliedCode.textContent = "";

        totalText.textContent = formatMoney(originalTotal);

        removeBtn.style.display = "none";
        applyBtn.disabled = false;
        applyBtn.textContent = "Áp dụng";
    }

    // TẠO PHẦN TỬ GIAO DIỆN AN TOÀN
    function createElement(tag, className, value) {

        const element = document.createElement(tag);

        if (className) {
            element.className = className;
        }

        if (value !== undefined) {
            element.textContent = value;
        }

        return element;
    }

    // TẢI TẤT CẢ MÓN TRONG GIỎ HÀNG TỪ SERVER
    async function loadCart() {

        cartLoaded = false;
        resetPromotion();

        cartList.textContent = "Đang tải giỏ hàng...";

        const response = await fetch("/cart?cartData=1", {
            method: "GET",
            headers: {
                "Accept": "application/json"
            },
            cache: "no-store"
        });

        if (!response.ok) {
            throw new Error("Không tải được giỏ hàng");
        }

        const data = await response.json();

        if (!data.success) {
            cartList.textContent = data.message;
            showMessage(data.message, false);
            return;
        }

        cartLoaded = true;

        originalTotal = Number(data.total);

        // CẬP NHẬT TỔNG TIỀN THỰC TẾ
        subtotalText.textContent =
            formatMoney(data.subtotal);

        shippingText.textContent =
            formatMoney(data.shipping);

        totalText.textContent =
            formatMoney(data.total);

        // XÓA DANH SÁCH CŨ
        cartList.replaceChildren();

        if (!data.items || data.items.length === 0) {
            cartList.textContent = "Giỏ hàng chưa có món ăn.";
            return;
        }

        // HIỂN THỊ TỪNG MÓN TRONG GIỎ
        data.items.forEach(function (item) {

            const row = createElement("div", "cart-line");

            // ẢNH SẢN PHẨM
            if (item.imageUrl) {

                const image = document.createElement("img");

                image.src = item.imageUrl;
                image.alt = item.productName || "Món ăn";
                image.loading = "lazy";

                row.appendChild(image);
            }

            const details = createElement("div");

            const name = createElement(
                "strong",
                "name",
                item.productName
            );

            const quantity = createElement(
                "div",
                "small muted",
                formatMoney(item.unitPrice) +
                " × " + item.quantity
            );

            details.appendChild(name);
            details.appendChild(quantity);

            const amount = createElement(
                "strong",
                "",
                formatMoney(item.lineTotal)
            );

            row.appendChild(details);
            row.appendChild(amount);

            cartList.appendChild(row);
        });

    }

    // ÁP DỤNG MÃ KHUYẾN MÃI
    async function applyPromotion() {

        const code = input.value.trim();

        if (!cartLoaded) {
            showMessage(
                "Không thể áp dụng mã khi chưa tải giỏ hàng.",
                false
            );
            return;
        }

        if (!code) {
            showMessage(
                "Vui lòng nhập mã khuyến mãi!",
                false
            );
            return;
        }

        const currentVersion = ++requestVersion;

        applyBtn.disabled = true;
        applyBtn.textContent = "Đang kiểm tra...";

        try {

            const response = await fetch(
                "/cart?promo=" + encodeURIComponent(code),
                {
                    method: "GET",
                    headers: {
                        "Accept": "application/json"
                    },
                    cache: "no-store"
                }
            );

            if (!response.ok) {
                throw new Error("Lỗi kiểm tra mã");
            }

            const data = await response.json();

            if (currentVersion !== requestVersion) {
                return;
            }

            // MÃ KHÔNG HỢP LỆ
            if (!data.success) {

                resetPromotion();

                showMessage(data.message, false);
                return;
            }

            // CẬP NHẬT GIÁ TRỊ GIỎ HÀNG TỪ SERVER
            originalTotal =
                Number(data.subtotal) + Number(data.shipping);

            subtotalText.textContent =
                formatMoney(data.subtotal);

            shippingText.textContent =
                formatMoney(data.shipping);

            // HIỂN THỊ SỐ TIỀN GIẢM
            discountText.textContent =
                "-" + formatMoney(data.discount);

            appliedCode.textContent =
                "(" + data.code + ")";

            discountRow.style.display = "flex";

            // HIỂN THỊ TỔNG SAU KHUYẾN MÃI
            totalText.textContent =
                formatMoney(data.total);

            removeBtn.style.display = "inline-block";

            showMessage(data.message, true);

        } catch (error) {

            if (currentVersion !== requestVersion) {
                return;
            }

            resetPromotion();

            showMessage(
                "Không thể kiểm tra mã. Vui lòng thử lại!",
                false
            );

            console.error(error);

        } finally {

            if (currentVersion === requestVersion) {
                applyBtn.disabled = false;
                applyBtn.textContent = "Áp dụng";
            }
        }
    }

    // NÚT ÁP DỤNG
    applyBtn.addEventListener("click", applyPromotion);

    // NHẤN ENTER
    input.addEventListener("keydown", function (event) {

        if (event.key === "Enter") {
            event.preventDefault();
            applyPromotion();
        }
    });

    // THAY MÃ THÌ BỎ KẾT QUẢ CŨ
    input.addEventListener("input", function () {

        resetPromotion();
        message.style.display = "none";
    });

    // HỦY MÃ
    removeBtn.addEventListener("click", function () {

        resetPromotion();

        input.value = "";

        showMessage("Đã hủy mã khuyến mãi.", true);
    });

    // TẢI GIỎ HÀNG KHI MỞ TRANG
    loadCart().catch(function (error) {

        cartLoaded = false;
        cartList.textContent = "Không thể tải giỏ hàng.";

        showMessage(
            "Có lỗi khi tải giỏ hàng. Vui lòng thử lại!",
            false
        );

        console.error(error);
    });

    // CHO PHÉP GỌI LẠI KHI GIỎ HÀNG THAY ĐỔI
    window.fitfoodReloadCart = function () {

        return loadCart().catch(function (error) {
            showMessage("Không thể cập nhật giỏ hàng.", false);
            console.error(error);
        });
    };

});
