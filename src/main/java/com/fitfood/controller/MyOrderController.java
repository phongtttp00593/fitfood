package com.fitfood.controller;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Khách hàng theo dõi trạng thái và tự hủy đơn của mình (/don-hang-cua-toi).
 * Chỉ đọc/ghi bảng đơn hàng + giao hàng bằng JdbcTemplate, giống OrderController.
 * SecurityConfig đã yêu cầu đăng nhập cho đường dẫn này (anyRequest().authenticated()).
 */
@Controller
public class MyOrderController {

    private static final String STATUS_PENDING = "Chờ xác nhận";
    private static final String STATUS_CANCELLED = "Đã hủy";
    private static final String REDIRECT_MY_ORDERS = "redirect:/don-hang-cua-toi";
    private static final String FLASH_ERROR = "error";
    private static final String FLASH_SUCCESS = "success";

    private final JdbcTemplate jdbc;

    public MyOrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** GET /don-hang-cua-toi - danh sách đơn của người đang đăng nhập, mới nhất trước. */
    @GetMapping("/don-hang-cua-toi")
    public String list(Authentication auth, Model model) {
        List<MyOrder> orders = jdbc.query("""
            SELECT o.id, o.placed_at, o.status, o.total,
                   (SELECT COUNT(*) FROM dbo.fitfood_order_items AS oi WHERE oi.order_id = o.id) AS item_count,
                   d.status AS delivery_status, d.sent_at, d.delivered_at
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_deliveries AS d ON d.order_id = o.id
            WHERE LOWER(u.email) = LOWER(?)
            ORDER BY o.placed_at DESC, o.id DESC
            """, (rs, rowNum) -> {
                String status = rs.getString("status");
                String deliveryStatus = rs.getString("delivery_status");
                return new MyOrder(
                        rs.getLong("id"),
                        toLocal(rs.getTimestamp("placed_at")),
                        status,
                        rs.getBigDecimal("total"),
                        rs.getInt("item_count"),
                        deliveryStatus,
                        toLocal(rs.getTimestamp("sent_at")),
                        toLocal(rs.getTimestamp("delivered_at")),
                        step(status, deliveryStatus),
                        STATUS_PENDING.equals(status));
            }, auth.getName());
        model.addAttribute("orders", orders);
        return "my-orders";
    }

    /**
     * POST /don-hang-cua-toi/{id}/huy - khách tự hủy đơn của chính mình.
     * Chỉ hủy được khi đơn còn "Chờ xác nhận", chưa thanh toán và chưa giao.
     * Điều kiện nằm ngay trong câu UPDATE nên không bị đổi trạng thái chen ngang.
     */
    @PostMapping("/don-hang-cua-toi/{id}/huy")
    public String cancel(@PathVariable long id, Authentication auth, RedirectAttributes redirect) {
        int updated = jdbc.update("""
            UPDATE o SET o.status = ?
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            WHERE o.id = ? AND LOWER(u.email) = LOWER(?) AND o.status = ?
              AND NOT EXISTS (SELECT 1 FROM dbo.fitfood_payments AS p
                              WHERE p.order_id = o.id AND p.status = N'Đã thanh toán')
              AND NOT EXISTS (SELECT 1 FROM dbo.fitfood_deliveries AS d
                              WHERE d.order_id = o.id AND d.status IN (N'Đang giao', N'Đã giao'))
            """, STATUS_CANCELLED, id, auth.getName(), STATUS_PENDING);
        if (updated == 0) {
            redirect.addFlashAttribute(FLASH_ERROR,
                    "Không thể hủy đơn này: đơn không tồn tại, đã được xác nhận/thanh toán hoặc đang giao.");
        } else {
            redirect.addFlashAttribute(FLASH_SUCCESS, "Đã hủy đơn hàng #" + id + ".");
        }
        return REDIRECT_MY_ORDERS;
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /**
     * Bước tiến trình hiển thị: 1 Chờ xác nhận, 2 Đang xử lý, 3 Đang giao, 4 Hoàn thành,
     * 0 = Đã hủy. Phiếu giao "Đang giao"/"Đã giao" được ưu tiên hơn trạng thái đơn.
     */
    private static int step(String orderStatus, String deliveryStatus) {
        if (STATUS_CANCELLED.equals(orderStatus)) return 0;
        if ("Hoàn thành".equals(orderStatus) || "Đã giao".equals(deliveryStatus)) return 4;
        if ("Đang giao".equals(orderStatus) || "Đang giao".equals(deliveryStatus)) return 3;
        if ("Đang xử lý".equals(orderStatus)) return 2;
        return 1;
    }

    public record MyOrder(long id, LocalDateTime placedAt, String status, BigDecimal total,
                          int itemCount, String deliveryStatus, LocalDateTime sentAt,
                          LocalDateTime deliveredAt, int step, boolean cancellable) {
    }
}
