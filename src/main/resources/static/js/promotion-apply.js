/* 
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/javascript.js to edit this template
 */


document.addEventListener("DOMContentLoaded", function () {

    // LẤY CÁC THÀNH PHẦN GIAO DIỆN
    const promoInput =
        document.getElementById("promo-code");

    const applyButton =
        document.getElementById("apply-promo");

    const removeButton =
        document.getElementById("remove-promo");

    const message =
        document.getElementById("promo-message");

    const discountRow =
        document.getElementById("promo-discount-row");

    const discountText =
        document.getElementById("promo-discount");

    const appliedCode =
        document.getElementById("applied-promo-code");

    const cartTotal =
        document.getElementById("cart-total");

    // Kiểm tra giao diện tồn tại
    if (!promoInput || !applyButton ||
        !removeButton || !message ||
        !discountRow || !discountText ||
        !appliedCode || !cartTotal) {
        return;
    }

    // Tổng tiền giỏ hàng demo
    const ORIGINAL_TOTAL = 152000;

    // Mỗi yêu cầu mang số thứ tự riêng
    // để tránh kết quả cũ ghi đè kết quả mới
    let requestVersion = 0;

    // ĐỊNH DẠNG TIỀN
    function formatMoney(amount) {
        return Number(amount).toLocaleString("vi-VN") + "đ";
    }

    // HIỂN THỊ THÔNG BÁO
    function showMessage(text, success) {

        message.textContent = text;
        message.style.display = "block";

        message.style.color =
            success ? "#15803d" : "#dc2626";
    }

    // KHÔI PHỤC TỔNG TIỀN GỐC
    function resetPromotion() {

        requestVersion++;

        discountRow.style.display = "none";

        discountText.textContent = "-0đ";
        appliedCode.textContent = "";

        cartTotal.textContent =
            formatMoney(ORIGINAL_TOTAL);

        removeButton.style.display = "none";

        applyButton.disabled = false;
        applyButton.textContent = "Áp dụng";
    }

    // ÁP DỤNG MÃ KHUYẾN MÃI
    async function applyPromotion() {

        const code = promoInput.value.trim();

        if (!code) {
            showMessage(
                "Vui lòng nhập mã khuyến mãi!",
                false
            );
            return;
        }

        const currentVersion = ++requestVersion;

        applyButton.disabled = true;
        applyButton.textContent = "Đang kiểm tra...";

        message.style.display = "none";

        try {

            // GỬI MÃ SANG CONTROLLER RIÊNG
            const url =
                "/cart?promo=" + encodeURIComponent(code);

            const response = await fetch(url, {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                },
                cache: "no-store"
            });

            if (!response.ok) {
                throw new Error("Không thể kết nối máy chủ");
            }

            const data = await response.json();

            // Không sử dụng kết quả đã cũ
            if (currentVersion !== requestVersion) {
                return;
            }

            // MÃ HỢP LỆ
            if (data.success === true) {

                discountText.textContent =
                    "-" + formatMoney(data.discount);

                cartTotal.textContent =
                    formatMoney(data.total);

                appliedCode.textContent =
                    "(" + data.code + ")";

                discountRow.style.display = "flex";
                removeButton.style.display = "inline-block";

                showMessage(
                    data.message,
                    true
                );

            } else {

                // MÃ KHÔNG HỢP LỆ
                resetPromotion();

                showMessage(
                    data.message || "Mã không hợp lệ!",
                    false
                );
            }

        } catch (error) {

            if (currentVersion !== requestVersion) {
                return;
            }

            resetPromotion();

            showMessage(
                "Có lỗi khi kiểm tra mã. Vui lòng thử lại!",
                false
            );

            console.error(error);

        } finally {

            if (currentVersion === requestVersion) {
                applyButton.disabled = false;
                applyButton.textContent = "Áp dụng";
            }
        }
    }

    // NHẤN NÚT ÁP DỤNG
    applyButton.addEventListener(
        "click",
        applyPromotion
    );

    // NHẤN ENTER ĐỂ ÁP DỤNG
    promoInput.addEventListener(
        "keydown",
        function (event) {

            if (event.key === "Enter") {
                event.preventDefault();
                applyPromotion();
            }
        }
    );

    // THAY ĐỔI MÃ THÌ XÓA KẾT QUẢ CŨ
    promoInput.addEventListener(
        "input",
        function () {

            resetPromotion();
            message.style.display = "none";
        }
    );

    // HỦY MÃ KHUYẾN MÃI
    removeButton.addEventListener(
        "click",
        function () {

            resetPromotion();

            promoInput.value = "";

            showMessage(
                "Đã hủy mã khuyến mãi.",
                true
            );
        }
    );

});


