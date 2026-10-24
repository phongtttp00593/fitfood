/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


package com.fitfood.controller;

import com.fitfood.promotion.Promotion;
import com.fitfood.promotion.PromotionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotionApplyController {

    private final PromotionService promotionService;

    // Giá trị hiện tại của giỏ hàng mẫu
    private static final BigDecimal SUBTOTAL =
            new BigDecimal("137000");

    private static final BigDecimal SHIPPING =
            new BigDecimal("15000");

    public PromotionApplyController(
            PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    // Chỉ nhận yêu cầu có tham số promo.
    // Không xung đột GET /cart của HomeController.
    @GetMapping(value = "/cart", params = "promo")
    public ResponseEntity<Map<String, Object>> applyPromotion(
            @RequestParam("promo") String code) {

        BigDecimal originalTotal = SUBTOTAL.add(SHIPPING);

        // 1. Kiểm tra mã trống
        if (code == null || code.isBlank()) {
            return fail(
                    "Vui lòng nhập mã khuyến mãi!",
                    originalTotal
            );
        }

        String normalizedCode =
                code.trim().toUpperCase(Locale.ROOT);

        // Giới hạn độ dài mã
        if (normalizedCode.length() > 50) {
            return fail(
                    "Mã khuyến mãi không hợp lệ!",
                    originalTotal
            );
        }

        // 2. Tìm mã từ dữ liệu khuyến mãi hiện có
        List<Promotion> promotions =
                promotionService.findAll(null);

        Promotion promotion = promotions.stream()
                .filter(p -> p.getCode() != null)
                .filter(p ->
                        p.getCode().equalsIgnoreCase(normalizedCode))
                .findFirst()
                .orElse(null);

        if (promotion == null) {
            return fail(
                    "Mã khuyến mãi không tồn tại!",
                    originalTotal
            );
        }

        // 3. Kiểm tra hiệu lực
        if (!promotion.isCurrentlyValid()) {
            return fail(
                    "Mã khuyến mãi đã hết hạn, "
                    + "chưa có hiệu lực hoặc đã bị tắt!",
                    originalTotal
            );
        }

        // 4. Kiểm tra điều kiện đơn tối thiểu
        BigDecimal minimumOrder =
                promotion.getMinimumOrder() == null
                ? BigDecimal.ZERO
                : promotion.getMinimumOrder();

        if (SUBTOTAL.compareTo(minimumOrder) < 0) {
            return fail(
                    "Đơn hàng phải đạt tối thiểu "
                    + formatMoney(minimumOrder),
                    originalTotal
            );
        }

        // 5. Tính giảm giá
        BigDecimal discount = BigDecimal.ZERO;

        // Giảm theo phần trăm
        if (promotion.getPercentDiscount() != null) {

            BigDecimal percent =
                    promotion.getPercentDiscount();

            discount = SUBTOTAL
                    .multiply(percent)
                    .divide(
                            new BigDecimal("100"),
                            0,
                            RoundingMode.HALF_UP
                    );

        } else if (promotion.getAmountDiscount() != null) {

            // Giảm theo số tiền cố định
            discount = promotion.getAmountDiscount();
        }

        // 6. Giới hạn giảm tối đa
        if (promotion.getMaximumDiscount() != null) {
            discount = discount.min(
                    promotion.getMaximumDiscount()
            );
        }

        // 7. Không cho giảm âm hoặc vượt tiền hàng
        discount = discount.max(BigDecimal.ZERO);
        discount = discount.min(SUBTOTAL);

        // 8. Tổng tiền cuối cùng
        BigDecimal total =
                originalTotal.subtract(discount);

        // 9. Kết quả thành công
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Áp dụng mã khuyến mãi thành công!",
                "code", promotion.getCode(),
                "discount", discount,
                "subtotal", SUBTOTAL,
                "shipping", SHIPPING,
                "total", total
        ));
    }

    // Phản hồi lỗi
    private ResponseEntity<Map<String, Object>> fail(
            String message,
            BigDecimal originalTotal) {

        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", message,
                "discount", BigDecimal.ZERO,
                "total", originalTotal
        ));
    }

    private String formatMoney(BigDecimal amount) {
        return String.format(
                "%,.0fđ",
                amount.doubleValue()
        ).replace(",", ".");
    }
}

