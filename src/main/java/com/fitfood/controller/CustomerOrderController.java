
package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.auth.repository.CartItemRepository;
import com.fitfood.auth.repository.CartRepository;
import com.fitfood.category.Cart;
import com.fitfood.category.CartItem;
import com.fitfood.promotion.Promotion;
import com.fitfood.promotion.PromotionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Xử lý nghiệp vụ phía Khách hàng cho:
 * 1) Đặt hàng từ giỏ hàng (POST /cart/checkout)
 * 2) Xem lịch sử & Theo dõi trạng thái đơn hàng (GET /tai-khoan/don-hang, GET /tai-khoan/don-hang/{id})
 * 3) Hủy đặt hàng khi đơn còn ở trạng thái "Chờ xác nhận" (POST /tai-khoan/don-hang/{id}/huy)
 */
@Controller
public class CustomerOrderController {

    private static final BigDecimal SHIPPING_FEE = new BigDecimal("15000");
    private static final List<String> FILTER_STATUSES = List.of(
            "Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy");
    private static final Set<String> VALID_FILTERS = Set.copyOf(FILTER_STATUSES);

    private final JdbcTemplate jdbc;
    private final AppUserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PromotionService promotionService;

    public CustomerOrderController(
            JdbcTemplate jdbc,
            AppUserRepository userRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            PromotionService promotionService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.promotionService = promotionService;
    }

    private AppUser getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
    }

    /**
     * POST /cart/checkout
     * Tạo đơn hàng mới từ giỏ hàng hiện tại, lưu chi tiết món, tạo phiếu giao "Chờ giao",
     * lưu thông tin thanh toán, trừ tồn kho và làm trống giỏ hàng.
     */
    @PostMapping("/cart/checkout")
    @Transactional
    public String checkout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("receiverName") String receiverName,
            @RequestParam("receiverPhone") String receiverPhone,
            @RequestParam("deliveryAddress") String deliveryAddress,
            @RequestParam(value = "paymentMethod", defaultValue = "COD") String paymentMethod,
            @RequestParam(value = "promotionCode", required = false) String promotionCode,
            @RequestParam(value = "orderNote", required = false) String orderNote,
            RedirectAttributes redirect) {

        AppUser user = getCurrentUser(userDetails);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        String name = receiverName == null ? "" : receiverName.strip();
        String phone = receiverPhone == null ? "" : receiverPhone.strip();
        String address = deliveryAddress == null ? "" : deliveryAddress.strip();
        String note = (orderNote == null || orderNote.isBlank()) ? null : orderNote.strip();

        if (name.isEmpty() || name.length() > 100
                || address.isEmpty() || address.length() > 255) {
            redirect.addFlashAttribute("warningMessage",
                    "Vui lòng nhập đầy đủ họ tên và địa chỉ nhận hàng hợp lệ.");
            return "redirect:/cart";
        }
        if (!phone.matches("[0-9]{10}")) {
            redirect.addFlashAttribute("warningMessage",
                    "Số điện thoại phải gồm đúng 10 chữ số (0-9).");
            return "redirect:/cart";
        }

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirect.addFlashAttribute("warningMessage", "Giỏ hàng của bạn đang trống.");
            return "redirect:/cart";
        }

        List<CartItem> items = new ArrayList<>(cart.getItems());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            if (item.getQuantity() <= 0 || item.getUnitPrice() == null) {
                continue;
            }
            subtotal = subtotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            redirect.addFlashAttribute("warningMessage", "Giỏ hàng không có sản phẩm hợp lệ để đặt hàng.");
            return "redirect:/cart";
        }

        Long promotionId = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (promotionCode != null && !promotionCode.isBlank()) {
            String normalizedCode = promotionCode.strip().toUpperCase(Locale.ROOT);
            Promotion promo = promotionService.findAll(null).stream()
                    .filter(p -> p.getCode() != null && p.getCode().equalsIgnoreCase(normalizedCode))
                    .findFirst()
                    .orElse(null);
            if (promo != null && promo.isCurrentlyValid()) {
                BigDecimal minOrder = promo.getMinimumOrder() == null ? BigDecimal.ZERO : promo.getMinimumOrder();
                if (subtotal.compareTo(minOrder) >= 0) {
                    if (promo.getPercentDiscount() != null) {
                        discount = subtotal.multiply(promo.getPercentDiscount())
                                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
                    } else if (promo.getAmountDiscount() != null) {
                        discount = promo.getAmountDiscount();
                    }
                    if (promo.getMaximumDiscount() != null) {
                        discount = discount.min(promo.getMaximumDiscount());
                    }
                    discount = discount.max(BigDecimal.ZERO).min(subtotal);
                    promotionId = promo.getId();
                }
            }
        }

        BigDecimal deliveryFee = SHIPPING_FEE;
        BigDecimal total = subtotal.subtract(discount).add(deliveryFee);

        final Long finalPromoId = promotionId;
        final BigDecimal finalSubtotal = subtotal;
        final BigDecimal finalDiscount = discount;
        final BigDecimal finalDeliveryFee = deliveryFee;
        final BigDecimal finalTotal = total;
        final String finalNote = note;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO dbo.fitfood_orders
                    (user_id, promotion_id, placed_at, subtotal, discount, delivery_fee, total, status, note)
                VALUES (?, ?, SYSDATETIME(), ?, ?, ?, ?, N'Chờ xác nhận', ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            if (finalPromoId != null) {
                ps.setLong(2, finalPromoId);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setBigDecimal(3, finalSubtotal);
            ps.setBigDecimal(4, finalDiscount);
            ps.setBigDecimal(5, finalDeliveryFee);
            ps.setBigDecimal(6, finalTotal);
            if (finalNote != null) {
                ps.setString(7, finalNote);
            } else {
                ps.setNull(7, java.sql.Types.NVARCHAR);
            }
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            redirect.addFlashAttribute("warningMessage", "Không thể tạo đơn hàng. Vui lòng thử lại.");
            return "redirect:/cart";
        }
        long orderId = generatedId.longValue();

        for (CartItem item : items) {
            if (item.getProduct() == null || item.getQuantity() <= 0) {
                continue;
            }
            Long productId = item.getProduct().getId();
            int qty = item.getQuantity();
            BigDecimal unitPrice = item.getUnitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

            String displayProductName;
            if (item.getMealPlanName() != null && !item.getMealPlanName().isBlank()) {
                displayProductName = "Gói ăn: " + item.getMealPlanName().strip();
            } else {
                displayProductName = item.getProduct().getName();
                if (item.getFoodNote() != null && !item.getFoodNote().isBlank()) {
                    String suffix = " (" + item.getFoodNote().strip() + ")";
                    if (displayProductName.length() + suffix.length() <= 150) {
                        displayProductName = displayProductName + suffix;
                    }
                }
            }
            if (displayProductName.length() > 150) {
                displayProductName = displayProductName.substring(0, 150);
            }

            jdbc.update("""
                INSERT INTO dbo.fitfood_order_items
                    (order_id, product_id, product_name, quantity, unit_price, line_total)
                VALUES (?, ?, ?, ?, ?, ?)
                """, orderId, productId, displayProductName, qty, unitPrice, lineTotal);

            if (item.getMealPlanName() == null || item.getMealPlanName().isBlank()) {
                jdbc.update("""
                    UPDATE dbo.fitfood_products
                    SET stock = CASE WHEN stock >= ? THEN stock - ? ELSE 0 END
                    WHERE id = ?
                    """, qty, qty, productId);
            }
        }

        jdbc.update("""
            INSERT INTO dbo.fitfood_deliveries
                (order_id, receiver_name, receiver_phone, delivery_address, status, note)
            VALUES (?, ?, ?, ?, N'Chờ giao', ?)
            """, orderId, name, phone, address, finalNote);

        String normalizedPaymentMethod = "BANK".equalsIgnoreCase(paymentMethod)
                ? "Chuyển khoản ngân hàng"
                : "Thanh toán khi nhận hàng (COD)";
        jdbc.update("""
            INSERT INTO dbo.fitfood_payments
                (order_id, payment_method, amount, status)
            VALUES (?, ?, ?, N'Chưa thanh toán')
            """, orderId, normalizedPaymentMethod, finalTotal);

        jdbc.update("""
            IF EXISTS (SELECT 1 FROM dbo.fitfood_customer_profiles WHERE user_id = ?)
            BEGIN
                UPDATE dbo.fitfood_customer_profiles
                SET phone = COALESCE(NULLIF(phone, ''), ?),
                    address = COALESCE(NULLIF(address, N''), ?)
                WHERE user_id = ?;
            END
            ELSE
            BEGIN
                INSERT INTO dbo.fitfood_customer_profiles (user_id, phone, address)
                VALUES (?, ?, ?);
            END
            """, user.getId(), phone, address, user.getId(), user.getId(), phone, address);

        jdbc.update("DELETE FROM dbo.fitfood_cart_items WHERE cart_id = ?", cart.getId());

        redirect.addFlashAttribute("orderSuccess",
                "Đặt hàng thành công! Mã đơn hàng của bạn là #" + orderId + ".");
        return "redirect:/tai-khoan/don-hang/" + orderId;
    }

    /**
     * GET /tai-khoan/don-hang
     * Danh sách lịch sử đơn hàng & theo dõi trạng thái đơn hàng của khách hàng.
     */
    @GetMapping("/tai-khoan/don-hang")
    public String myOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "all") String status,
            Model model) {

        AppUser user = getCurrentUser(userDetails);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        String normalizedFilter = VALID_FILTERS.contains(status) ? status : "all";

        List<Object> params = new ArrayList<>();
        params.add(user.getId());

        StringBuilder sql = new StringBuilder("""
            SELECT o.id, o.placed_at, o.subtotal, o.discount, o.delivery_fee, o.total,
                   o.status, o.note,
                   d.receiver_name, d.receiver_phone, d.delivery_address,
                   d.status AS delivery_status, d.sent_at, d.delivered_at,
                   pm.payment_method, pm.status AS payment_status,
                   pr.code AS promotion_code,
                   (SELECT COUNT(*) FROM dbo.fitfood_invoices i WHERE i.order_id = o.id) AS invoice_count
            FROM dbo.fitfood_orders o
            LEFT JOIN dbo.fitfood_deliveries d ON d.order_id = o.id
            OUTER APPLY (
                SELECT TOP 1 p.payment_method, p.status
                FROM dbo.fitfood_payments p
                WHERE p.order_id = o.id
                ORDER BY p.id DESC
            ) pm
            LEFT JOIN dbo.fitfood_promotions pr ON pr.id = o.promotion_id
            WHERE o.user_id = ?
            """);

        if (!"all".equals(normalizedFilter)) {
            sql.append(" AND o.status = ?");
            params.add(normalizedFilter);
        }
        sql.append(" ORDER BY o.placed_at DESC, o.id DESC");

        List<CustomerOrderView> orders = jdbc.query(sql.toString(), (rs, rowNum) -> {
            long orderId = rs.getLong("id");
            Timestamp placedAt = rs.getTimestamp("placed_at");
            Timestamp sentAt = rs.getTimestamp("sent_at");
            Timestamp deliveredAt = rs.getTimestamp("delivered_at");
            String orderStatus = rs.getString("status");
            String deliveryStatus = rs.getString("delivery_status");
            String paymentStatus = rs.getString("payment_status");
            int invoiceCount = rs.getInt("invoice_count");

            boolean canCancel = isCancellable(orderStatus, deliveryStatus, paymentStatus, invoiceCount);
            int stepIndex = computeStepIndex(orderStatus);

            List<OrderItemView> items = jdbc.query("""
                SELECT id, product_id, product_name, quantity, unit_price, line_total
                FROM dbo.fitfood_order_items
                WHERE order_id = ?
                ORDER BY id
                """, (itemRs, idx) -> new OrderItemView(
                    itemRs.getLong("id"),
                    itemRs.getLong("product_id"),
                    itemRs.getString("product_name"),
                    itemRs.getInt("quantity"),
                    itemRs.getBigDecimal("unit_price"),
                    itemRs.getBigDecimal("line_total")
            ), orderId);

            return new CustomerOrderView(
                    orderId,
                    placedAt == null ? null : placedAt.toLocalDateTime(),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("delivery_fee"),
                    rs.getBigDecimal("total"),
                    orderStatus,
                    rs.getString("note"),
                    rs.getString("receiver_name"),
                    rs.getString("receiver_phone"),
                    rs.getString("delivery_address"),
                    deliveryStatus == null ? "Chờ giao" : deliveryStatus,
                    sentAt == null ? null : sentAt.toLocalDateTime(),
                    deliveredAt == null ? null : deliveredAt.toLocalDateTime(),
                    rs.getString("payment_method") == null ? "Thanh toán khi nhận hàng (COD)" : rs.getString("payment_method"),
                    paymentStatus == null ? "Chưa thanh toán" : paymentStatus,
                    rs.getString("promotion_code"),
                    stepIndex,
                    canCancel,
                    items
            );
        }, params.toArray());

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("all", 0);
        for (String s : FILTER_STATUSES) {
            counts.put(s, 0);
        }
        jdbc.query("""
            SELECT status, COUNT(*) AS cnt
            FROM dbo.fitfood_orders
            WHERE user_id = ?
            GROUP BY status
            """, rs -> {
            String st = rs.getString("status");
            int cnt = rs.getInt("cnt");
            counts.put("all", counts.getOrDefault("all", 0) + cnt);
            if (st != null && counts.containsKey(st)) {
                counts.put(st, cnt);
            }
        }, user.getId());

        model.addAttribute("orders", orders);
        model.addAttribute("selectedStatus", normalizedFilter);
        model.addAttribute("filterStatuses", FILTER_STATUSES);
        model.addAttribute("statusCounts", counts);
        return "v2/my-orders";
    }

    /**
     * GET /tai-khoan/don-hang/{id}
     * Xem chi tiết tiến trình & trạng thái của 1 đơn hàng cụ thể.
     */
    @GetMapping("/tai-khoan/don-hang/{id}")
    public String orderTrackingDetail(
            @PathVariable("id") long orderId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirect,
            Model model) {

        AppUser user = getCurrentUser(userDetails);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        List<CustomerOrderView> matches = jdbc.query("""
            SELECT o.id, o.placed_at, o.subtotal, o.discount, o.delivery_fee, o.total,
                   o.status, o.note,
                   d.receiver_name, d.receiver_phone, d.delivery_address,
                   d.status AS delivery_status, d.sent_at, d.delivered_at,
                   pm.payment_method, pm.status AS payment_status,
                   pr.code AS promotion_code,
                   (SELECT COUNT(*) FROM dbo.fitfood_invoices i WHERE i.order_id = o.id) AS invoice_count
            FROM dbo.fitfood_orders o
            LEFT JOIN dbo.fitfood_deliveries d ON d.order_id = o.id
            OUTER APPLY (
                SELECT TOP 1 p.payment_method, p.status
                FROM dbo.fitfood_payments p
                WHERE p.order_id = o.id
                ORDER BY p.id DESC
            ) pm
            LEFT JOIN dbo.fitfood_promotions pr ON pr.id = o.promotion_id
            WHERE o.id = ? AND o.user_id = ?
            """, (rs, rowNum) -> {
            Timestamp placedAt = rs.getTimestamp("placed_at");
            Timestamp sentAt = rs.getTimestamp("sent_at");
            Timestamp deliveredAt = rs.getTimestamp("delivered_at");
            String orderStatus = rs.getString("status");
            String deliveryStatus = rs.getString("delivery_status");
            String paymentStatus = rs.getString("payment_status");
            int invoiceCount = rs.getInt("invoice_count");

            boolean canCancel = isCancellable(orderStatus, deliveryStatus, paymentStatus, invoiceCount);
            int stepIndex = computeStepIndex(orderStatus);

            List<OrderItemView> items = jdbc.query("""
                SELECT id, product_id, product_name, quantity, unit_price, line_total
                FROM dbo.fitfood_order_items
                WHERE order_id = ?
                ORDER BY id
                """, (itemRs, idx) -> new OrderItemView(
                    itemRs.getLong("id"),
                    itemRs.getLong("product_id"),
                    itemRs.getString("product_name"),
                    itemRs.getInt("quantity"),
                    itemRs.getBigDecimal("unit_price"),
                    itemRs.getBigDecimal("line_total")
            ), orderId);

            return new CustomerOrderView(
                    orderId,
                    placedAt == null ? null : placedAt.toLocalDateTime(),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("delivery_fee"),
                    rs.getBigDecimal("total"),
                    orderStatus,
                    rs.getString("note"),
                    rs.getString("receiver_name"),
                    rs.getString("receiver_phone"),
                    rs.getString("delivery_address"),
                    deliveryStatus == null ? "Chờ giao" : deliveryStatus,
                    sentAt == null ? null : sentAt.toLocalDateTime(),
                    deliveredAt == null ? null : deliveredAt.toLocalDateTime(),
                    rs.getString("payment_method") == null ? "Thanh toán khi nhận hàng (COD)" : rs.getString("payment_method"),
                    paymentStatus == null ? "Chưa thanh toán" : paymentStatus,
                    rs.getString("promotion_code"),
                    stepIndex,
                    canCancel,
                    items
            );
        }, orderId, user.getId());

        if (matches.isEmpty()) {
            redirect.addFlashAttribute("orderError", "Không tìm thấy đơn hàng #" + orderId + ".");
            return "redirect:/tai-khoan/don-hang";
        }

        model.addAttribute("order", matches.get(0));
        return "v2/order-tracking";
    }

    /**
     * POST /tai-khoan/don-hang/{id}/huy
     * Khách hàng hủy đặt hàng.
     * Chỉ cho phép khi:
     *  - Đơn hàng thuộc về khách đang đăng nhập
     *  - Trạng thái đơn đang là "Chờ xác nhận"
     *  - Phiếu giao chưa ở trạng thái "Đang giao" / "Đã giao"
     *  - Đơn chưa thanh toán và chưa xuất hóa đơn
     * Khi hủy:
     *  - Cập nhật trạng thái đơn -> "Đã hủy" kèm lý do hủy
     *  - Hoàn lại tồn kho (stock) cho các sản phẩm trong đơn
     *  - Xóa phiếu giao "Chờ giao" (để không còn trong danh sách chờ giao của Admin)
     */
    @PostMapping("/tai-khoan/don-hang/{id}/huy")
    @Transactional
    public String cancelOrder(
            @PathVariable("id") long orderId,
            @RequestParam(value = "cancelReason", required = false) String cancelReason,
            @RequestParam(value = "reason", required = false) String fallbackReason,
            @RequestParam(value = "returnTo", defaultValue = "detail") String returnTo,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirect) {

        AppUser user = getCurrentUser(userDetails);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        List<CancelCheckRow> checks = jdbc.query("""
            SELECT o.status, o.note,
                   (SELECT TOP 1 d.status FROM dbo.fitfood_deliveries d WHERE d.order_id = o.id) AS delivery_status,
                   (SELECT COUNT(*) FROM dbo.fitfood_payments p WHERE p.order_id = o.id AND p.status = N'Đã thanh toán') AS paid_count,
                   (SELECT COUNT(*) FROM dbo.fitfood_invoices i WHERE i.order_id = o.id) AS invoice_count
            FROM dbo.fitfood_orders o WITH (UPDLOCK, HOLDLOCK)
            WHERE o.id = ? AND o.user_id = ?
            """, (rs, rowNum) -> new CancelCheckRow(
                rs.getString("status"),
                rs.getString("note"),
                rs.getString("delivery_status"),
                rs.getInt("paid_count"),
                rs.getInt("invoice_count")
        ), orderId, user.getId());

        String targetUrl = "list".equalsIgnoreCase(returnTo)
                ? "redirect:/tai-khoan/don-hang"
                : "redirect:/tai-khoan/don-hang/" + orderId;

        if (checks.isEmpty()) {
            redirect.addFlashAttribute("orderError", "Không tìm thấy đơn hàng cần hủy.");
            return "redirect:/tai-khoan/don-hang";
        }

        CancelCheckRow check = checks.get(0);
        if ("Đã hủy".equalsIgnoreCase(check.status())) {
            redirect.addFlashAttribute("orderError", "Đơn hàng #" + orderId + " đã được hủy trước đó.");
            return targetUrl;
        }
        if (!"Chờ xác nhận".equals(check.status())) {
            redirect.addFlashAttribute("orderError",
                    "Không thể hủy: Đơn hàng #" + orderId + " đang ở trạng thái \"" + check.status()
                            + "\" (bếp đang chuẩn bị hoặc đang giao).");
            return targetUrl;
        }
        if (check.deliveryStatus() != null
                && ("Đang giao".equals(check.deliveryStatus()) || "Đã giao".equals(check.deliveryStatus()))) {
            redirect.addFlashAttribute("orderError",
                    "Không thể hủy: Đơn hàng #" + orderId + " đã được bàn giao cho đơn vị vận chuyển.");
            return targetUrl;
        }
        if (check.paidCount() > 0 || check.invoiceCount() > 0) {
            redirect.addFlashAttribute("orderError",
                    "Không thể tự hủy đơn đã thanh toán hoặc đã xuất hóa đơn. Vui lòng liên hệ Chat hỗ trợ.");
            return targetUrl;
        }

        String effectiveReason = (cancelReason != null && !cancelReason.isBlank())
                ? cancelReason
                : fallbackReason;
        String reason = (effectiveReason == null || effectiveReason.isBlank())
                ? "Khách hàng yêu cầu hủy đơn"
                : "Lý do hủy: " + effectiveReason.strip();
        String updatedNote = (check.note() == null || check.note().isBlank())
                ? reason
                : check.note() + " | " + reason;
        if (updatedNote.length() > 500) {
            updatedNote = updatedNote.substring(0, 500);
        }

        jdbc.update("""
            UPDATE dbo.fitfood_orders
            SET status = N'Đã hủy', note = ?
            WHERE id = ? AND user_id = ?
            """, updatedNote, orderId, user.getId());

        List<Map<String, Object>> orderItems = jdbc.queryForList("""
            SELECT product_id, quantity, product_name
            FROM dbo.fitfood_order_items
            WHERE order_id = ?
            """, orderId);
        for (Map<String, Object> item : orderItems) {
            Long productId = ((Number) item.get("product_id")).longValue();
            int qty = ((Number) item.get("quantity")).intValue();
            String productName = (String) item.get("product_name");
            if (productName == null || !productName.startsWith("Gói ăn:")) {
                jdbc.update("""
                    UPDATE dbo.fitfood_products
                    SET stock = COALESCE(stock, 0) + ?
                    WHERE id = ?
                    """, qty, productId);
            }
        }

        jdbc.update("""
            DELETE FROM dbo.fitfood_deliveries
            WHERE order_id = ? AND status = N'Chờ giao'
            """, orderId);

        redirect.addFlashAttribute("orderSuccess",
                "Đã hủy đơn hàng #" + orderId + " thành công.");
        return targetUrl;
    }

    private static boolean isCancellable(
            String orderStatus,
            String deliveryStatus,
            String paymentStatus,
            int invoiceCount) {
        if (!"Chờ xác nhận".equals(orderStatus)) {
            return false;
        }
        if ("Đang giao".equals(deliveryStatus) || "Đã giao".equals(deliveryStatus)) {
            return false;
        }
        if ("Đã thanh toán".equals(paymentStatus)) {
            return false;
        }
        return invoiceCount == 0;
    }

    private static int computeStepIndex(String orderStatus) {
        if (orderStatus == null) {
            return 1;
        }
        return switch (orderStatus) {
            case "Chờ xác nhận" -> 1;
            case "Đang xử lý" -> 2;
            case "Đang giao" -> 3;
            case "Hoàn thành" -> 4;
            case "Đã hủy" -> -1;
            default -> 1;
        };
    }

    private record CancelCheckRow(
            String status,
            String note,
            String deliveryStatus,
            int paidCount,
            int invoiceCount) {
    }

    public record OrderItemView(
            long id,
            long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record CustomerOrderView(
            long id,
            LocalDateTime placedAt,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal deliveryFee,
            BigDecimal total,
            String status,
            String note,
            String receiverName,
            String receiverPhone,
            String deliveryAddress,
            String deliveryStatus,
            LocalDateTime sentAt,
            LocalDateTime deliveredAt,
            String paymentMethod,
            String paymentStatus,
            String promotionCode,
            int stepIndex,
            boolean canCancel,
            List<OrderItemView> items) {

        public String code() {
            return "#" + id;
        }

        public int progressStep() {
            return Math.max(stepIndex, 0);
        }

        public String cancelDisabledReason() {
            if ("Đã hủy".equals(status)) {
                return "Đơn hàng đã bị hủy.";
            }
            if ("Đã thanh toán".equals(paymentStatus)) {
                return "Đơn hàng đã thanh toán nên không thể hủy trực tuyến. Vui lòng liên hệ Chat hỗ trợ.";
            }
            return "Đơn hàng đang ở trạng thái \"" + status + "\" nên không thể hủy trực tuyến.";
        }

        public String transactionCode() {
            return null;
        }

        public LocalDateTime paidAt() {
            return null;
        }
    }
}
