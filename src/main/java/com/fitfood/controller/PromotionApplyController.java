
package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.promotion.Promotion;
import com.fitfood.promotion.PromotionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotionApplyController {

    private static final BigDecimal SHIPPING =
            new BigDecimal("15000");

    private final PromotionService promotionService;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;

    public PromotionApplyController(
            PromotionService promotionService,
            AppUserRepository appUserRepository,
            JdbcTemplate jdbcTemplate) {

        this.promotionService = promotionService;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // LẤY ID NGƯỜI DÙNG ĐANG ĐĂNG NHẬP
    private Long getCurrentUserId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return appUserRepository
                .findByEmailIgnoreCase(authentication.getName())
                .map(AppUser::getId)
                .orElse(null);
    }

    // LẤY TẤT CẢ MÓN TRONG GIỎ HÀNG
    private List<Map<String, Object>> getCartItems(Long userId) {

        String sql = """
            SELECT
                ci.id AS itemId,
                p.id AS productId,
                p.name AS productName,
                p.image_url AS imageUrl,
                ci.quantity AS quantity,
                ci.unit_price AS unitPrice,
                CAST(ci.unit_price * ci.quantity
                     AS DECIMAL(18,2)) AS lineTotal
            FROM dbo.fitfood_carts c
            JOIN dbo.fitfood_cart_items ci
                ON ci.cart_id = c.id
            JOIN dbo.fitfood_products p
                ON p.id = ci.product_id
            WHERE c.user_id = ?
                AND p.active = 1
                AND ci.quantity > 0
            ORDER BY ci.id
            """;

        return jdbcTemplate.queryForList(sql, userId);
    }

    // TÍNH TỔNG TIỀN TẤT CẢ CÁC MÓN
    private BigDecimal calculateSubtotal(
            List<Map<String, Object>> items) {

        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map<String, Object> item : items) {

            BigDecimal lineTotal =
                    (BigDecimal) item.get("lineTotal");

            subtotal = subtotal.add(lineTotal);
        }

        return subtotal;
    }

    // API LẤY GIỎ HÀNG THỰC TẾ
    @GetMapping(value = "/cart", params = "cartData")
    public ResponseEntity<Map<String, Object>> getCartData(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        if (userId == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Vui lòng đăng nhập để xem giỏ hàng."
            ));
        }

        List<Map<String, Object>> items = getCartItems(userId);

        BigDecimal subtotal = calculateSubtotal(items);

        BigDecimal shipping = items.isEmpty()
                ? BigDecimal.ZERO
                : SHIPPING;

        BigDecimal total = subtotal.add(shipping);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "items", items,
                "subtotal", subtotal,
                "shipping", shipping,
                "total", total
        ));
    }

    // API KIỂM TRA VÀ ÁP DỤNG MÃ KHUYẾN MÃI
    @GetMapping(value = "/cart", params = "promo")
    public ResponseEntity<Map<String, Object>> applyPromotion(
            @RequestParam("promo") String code,
            Authentication authentication) {

        // 1. Xác định người dùng
        Long userId = getCurrentUserId(authentication);

        if (userId == null) {
            return error(
                    "Vui lòng đăng nhập để áp dụng mã!",
                    BigDecimal.ZERO
            );
        }

        // 2. Lấy giỏ hàng thực tế
        List<Map<String, Object>> cartItems =
                getCartItems(userId);

        BigDecimal subtotal =
                calculateSubtotal(cartItems);

        BigDecimal shipping = cartItems.isEmpty()
                ? BigDecimal.ZERO
                : SHIPPING;

        BigDecimal originalTotal =
                subtotal.add(shipping);

        // 3. Kiểm tra giỏ hàng rỗng
        if (cartItems.isEmpty()
                || subtotal.compareTo(BigDecimal.ZERO) <= 0) {

            return error(
                    "Giỏ hàng chưa có món để áp dụng khuyến mãi!",
                    originalTotal
            );
        }

        // 4. Kiểm tra mã trống
        if (code == null || code.isBlank()) {
            return error(
                    "Vui lòng nhập mã khuyến mãi!",
                    originalTotal
            );
        }

        String normalizedCode =
                code.trim().toUpperCase(Locale.ROOT);

        if (normalizedCode.length() > 50) {
            return error(
                    "Mã khuyến mãi không hợp lệ!",
                    originalTotal
            );
        }

        // 5. Tìm mã trong hệ thống
        Promotion promotion = promotionService
                .findAll(null)
                .stream()
                .filter(p -> p.getCode() != null)
                .filter(p -> p.getCode()
                        .equalsIgnoreCase(normalizedCode))
                .findFirst()
                .orElse(null);

        if (promotion == null) {
            return error(
                    "Mã khuyến mãi không tồn tại!",
                    originalTotal
            );
        }

        // 6. Kiểm tra hiệu lực
        if (!promotion.isCurrentlyValid()) {
            return error(
                    "Mã khuyến mãi đã hết hạn hoặc bị tắt!",
                    originalTotal
            );
        }

        // 7. Kiểm tra giá trị đơn hàng tối thiểu
        BigDecimal minimumOrder =
                promotion.getMinimumOrder() == null
                        ? BigDecimal.ZERO
                        : promotion.getMinimumOrder();

        if (subtotal.compareTo(minimumOrder) < 0) {

            return error(
                    "Đơn hàng phải đạt tối thiểu "
                            + formatMoney(minimumOrder),
                    originalTotal
            );
        }

        // 8. Tính số tiền giảm
        BigDecimal discount = BigDecimal.ZERO;

        // Giảm theo phần trăm
        if (promotion.getPercentDiscount() != null) {

            BigDecimal percent =
                    promotion.getPercentDiscount();

            discount = subtotal
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

        // 9. Áp dụng giới hạn giảm tối đa
        if (promotion.getMaximumDiscount() != null) {

            discount = discount.min(
                    promotion.getMaximumDiscount()
            );
        }

        // 10. Không giảm quá giá trị tiền món
        discount = discount.max(BigDecimal.ZERO);
        discount = discount.min(subtotal);

        // 11. Tính tổng tiền cuối
        BigDecimal total = subtotal
                .subtract(discount)
                .add(shipping);

        // 12. Trả kết quả
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Áp dụng mã khuyến mãi thành công!",
                "code", promotion.getCode(),
                "subtotal", subtotal,
                "shipping", shipping,
                "discount", discount,
                "total", total
        ));
    }

    // TRẢ THÔNG BÁO LỖI
    private ResponseEntity<Map<String, Object>> error(
            String message,
            BigDecimal total) {

        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", message,
                "discount", BigDecimal.ZERO,
                "total", total
        ));
    }

    // ĐỊNH DẠNG TIỀN VNĐ
    private String formatMoney(BigDecimal amount) {

        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        Locale.forLanguageTag("vi-VN")
                );

        formatter.setMaximumFractionDigits(0);

        return formatter.format(amount) + "đ";
    }
}
