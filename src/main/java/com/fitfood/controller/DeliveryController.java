
package com.fitfood.controller;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/deliveries")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_GIAO_HANG')")
public class DeliveryController {

    // Danh sách trạng thái giao hàng
    private static final List<String> DELIVERY_STATUSES = List.of(
            "Chờ giao",
            "Đang giao",
            "Đã giao",
            "Giao thất bại"
    );

    private static final Set<String> VALID_STATUSES =
            Set.copyOf(DELIVERY_STATUSES);

    private static final String FLASH_ERROR = "error";

    private static final String FLASH_SUCCESS = "success";

    private static final String REDIRECT_DELIVERIES =
            "redirect:/admin/deliveries";

    private final JdbcTemplate jdbc;

    public DeliveryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ========================================
    // KIỂM TRA TRẠNG THÁI ĐƯỢC PHÉP CHUYỂN
    // ========================================

    private static List<String> allowedDeliveryStatuses(
            String delivery, String order) {

        List<String> allowed = new ArrayList<>();

        // Cho phép giữ nguyên trạng thái
        // để chỉnh sửa thông tin người nhận
        allowed.add(delivery);

        if ("Chờ giao".equals(delivery)
                && "Đang xử lý".equals(order)) {

            allowed.add("Đang giao");
            allowed.add("Giao thất bại");

        } else if ("Đang giao".equals(delivery)
        && "Đang giao".equals(order)) {

            allowed.add("Đã giao");
            allowed.add("Giao thất bại");
}

        return allowed;
    }

    // ========================================
    // DANH SÁCH GIAO HÀNG
    // ========================================

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "all") String filter,
            Model model) {

        String normalizedKeyword = keyword.strip();

        String normalizedFilter =
                VALID_STATUSES.contains(filter)
                ? filter : "all";

        String sql = """
            SELECT d.id,
                   d.order_id,
                   d.receiver_name,
                   d.receiver_phone,
                   d.delivery_address,
                   d.status,
                   d.sent_at,
                   d.delivered_at,
                   o.status AS order_status,
                   o.total,
                   u.full_name AS account_name,
                   u.email
            FROM dbo.fitfood_deliveries AS d
            JOIN dbo.fitfood_orders AS o
                ON o.id = d.order_id
            JOIN dbo.fitfood_users AS u
                ON u.id = o.user_id
            WHERE (? = 'all' OR d.status = ?)
              AND (
                  ? = ''
                  OR d.receiver_name LIKE ?
                  OR d.receiver_phone LIKE ?
                  OR d.delivery_address LIKE ?
                  OR u.email LIKE ?
                  OR CONVERT(VARCHAR(20), d.order_id) LIKE ?
              )
            ORDER BY
                CASE WHEN d.sent_at IS NULL
                     THEN 0 ELSE 1 END,
                d.sent_at DESC,
                d.id DESC
            """;

        String search = "%" + normalizedKeyword + "%";

        List<DeliveryRow> deliveries = jdbc.query(
                sql,
                (rs, rowNum) -> {

                    Timestamp sentAt =
                            rs.getTimestamp("sent_at");

                    Timestamp deliveredAt =
                            rs.getTimestamp("delivered_at");

                    return new DeliveryRow(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getString("receiver_name"),
                            rs.getString("receiver_phone"),
                            rs.getString("delivery_address"),
                            rs.getString("status"),

                            sentAt == null
                                    ? null
                                    : sentAt.toLocalDateTime(),

                            deliveredAt == null
                                    ? null
                                    : deliveredAt.toLocalDateTime(),

                            rs.getBigDecimal("total"),
                            rs.getString("account_name"),
                            rs.getString("email"),
                            rs.getString("order_status")
                    );
                },
                normalizedFilter,
                normalizedFilter,
                normalizedKeyword,
                search,
                search,
                search,
                search,
                search
        );

        // Lấy danh sách đơn chưa có phiếu giao
        List<OrderOption> ordersWithoutDelivery = jdbc.query("""
            SELECT o.id,
                   u.full_name,
                   u.email
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u
                ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_deliveries AS d
                ON d.order_id = o.id
            WHERE d.id IS NULL
              AND o.status IN (
                  N'Chờ xác nhận',
                  N'Đang xử lý'
              )
            ORDER BY o.placed_at DESC, o.id DESC
            """,
                (rs, rowNum) -> new OrderOption(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("email")
                )
        );

        model.addAttribute("deliveries", deliveries);

        model.addAttribute(
                "ordersWithoutDelivery",
                ordersWithoutDelivery
        );

        model.addAttribute(
                "deliveryStatuses",
                DELIVERY_STATUSES
        );

        model.addAttribute(
                "keyword",
                normalizedKeyword
        );

        model.addAttribute(
                "filter",
                normalizedFilter
        );

        return "admin-deliveries";
    }

    // ========================================
    // TẠO PHIẾU GIAO HÀNG
    // ========================================

    @PostMapping
    @Transactional
    public String create(
            @RequestParam long orderId,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone,
            @RequestParam String deliveryAddress,
            RedirectAttributes redirect) {

        String name = receiverName.strip();
        String phone = receiverPhone.strip();
        String address = deliveryAddress.strip();

        // Kiểm tra thông tin người nhận
        if (name.isEmpty()
                || name.length() > 100
                || phone.isEmpty()
                || phone.length() > 20
                || address.isEmpty()
                || address.length() > 255) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Vui lòng nhập tên, số điện thoại và địa chỉ hợp lệ."
            );

            return REDIRECT_DELIVERIES;
        }

        try {

            int inserted = jdbc.update("""
                INSERT INTO dbo.fitfood_deliveries (
                    order_id,
                    receiver_name,
                    receiver_phone,
                    delivery_address
                )
                SELECT o.id, ?, ?, ?
                FROM dbo.fitfood_orders AS o
                    WITH (UPDLOCK, HOLDLOCK)
                WHERE o.id = ?
                  AND o.status IN (
                      N'Chờ xác nhận',
                      N'Đang xử lý'
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.fitfood_deliveries AS d
                      WHERE d.order_id = o.id
                  )
                """,
                    name,
                    phone,
                    address,
                    orderId
            );

            if (inserted == 0) {

                redirect.addFlashAttribute(
                        FLASH_ERROR,
                        "Không thể tạo phiếu: đơn hàng không tồn tại, không còn hợp lệ hoặc đã có phiếu giao."
                );

            } else {

                // Chuyển đơn sang Đang xử lý
                jdbc.update("""
                    UPDATE dbo.fitfood_orders
                    SET status = N'Đang xử lý'
                    WHERE id = ?
                      AND status = N'Chờ xác nhận'
                    """, orderId);

                redirect.addFlashAttribute(
                        FLASH_SUCCESS,
                        "Đã tạo phiếu giao hàng thành công."
                );
            }

        } catch (DataIntegrityViolationException ex) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Đơn hàng đã có phiếu giao hoặc dữ liệu không hợp lệ."
            );
        }

        return REDIRECT_DELIVERIES;
    }

    // ========================================
    // CẬP NHẬT PHIẾU GIAO HÀNG
    // ========================================

    @PostMapping("/{id}/update")
    @Transactional
    public String update(
            @PathVariable long id,
            @RequestParam String status,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone,
            @RequestParam String deliveryAddress,
            RedirectAttributes redirect) {

        String name = receiverName.strip();
        String phone = receiverPhone.strip();
        String address = deliveryAddress.strip();

        // Kiểm tra dữ liệu đầu vào
        if (!VALID_STATUSES.contains(status)
                || name.isEmpty()
                || name.length() > 100
                || phone.isEmpty()
                || phone.length() > 20
                || address.isEmpty()
                || address.length() > 255) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Trạng thái hoặc thông tin người nhận không hợp lệ."
            );

            return REDIRECT_DELIVERIES;
        }

        // Lấy trạng thái hiện tại của đơn hàng
        List<String> orderStates = jdbc.query("""
            SELECT o.status
            FROM dbo.fitfood_orders AS o
                WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.fitfood_deliveries AS d
                ON d.order_id = o.id
            WHERE d.id = ?
            """,
                (rs, rowNum) -> rs.getString("status"),
                id
        );

        if (orderStates.isEmpty()) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Không tìm thấy đơn hàng."
            );

            return REDIRECT_DELIVERIES;
        }

        // Lấy trạng thái hiện tại của phiếu giao
        List<String> deliveryStates = jdbc.query("""
            SELECT status
            FROM dbo.fitfood_deliveries
                WITH (UPDLOCK, HOLDLOCK)
            WHERE id = ?
            """,
                (rs, rowNum) -> rs.getString("status"),
                id
        );

        if (deliveryStates.isEmpty()) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Không tìm thấy phiếu giao hàng."
            );

            return REDIRECT_DELIVERIES;
        }

        String oldStatus = deliveryStates.get(0);

        String orderStatus = orderStates.get(0);

        // Chặn chuyển lùi hoặc hủy đơn đang giao
        if (!allowedDeliveryStatuses(
                oldStatus,
                orderStatus
        ).contains(status)) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Không thể chuyển trạng thái từ "
                    + oldStatus
                    + " sang "
                    + status
                    + "."
            );

            return REDIRECT_DELIVERIES;
        }

        // Cập nhật thông tin giao hàng
        int updated = jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET receiver_name = ?,
                receiver_phone = ?,
                delivery_address = ?,
                status = ?,

                sent_at = CASE
                    WHEN ? = N'Đang giao'
                         AND sent_at IS NULL
                    THEN SYSDATETIME()
                    ELSE sent_at
                END,

                delivered_at = CASE
                    WHEN ? = N'Đã giao'
                         AND delivered_at IS NULL
                    THEN SYSDATETIME()

                    WHEN ? <> N'Đã giao'
                    THEN NULL

                    ELSE delivered_at
                END

            WHERE id = ?
            """,
                name,
                phone,
                address,
                status,
                status,
                status,
                status,
                id
        );

        if (updated == 0) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Không tìm thấy phiếu giao hàng cần cập nhật."
            );

            return REDIRECT_DELIVERIES;
        }

        // Lấy mã đơn hàng tương ứng
        Long orderId = jdbc.queryForObject("""
            SELECT order_id
            FROM dbo.fitfood_deliveries
            WHERE id = ?
            """,
                Long.class,
                id
        );

        // Chỉ đồng bộ khi trạng thái thay đổi
        if (orderId != null && !status.equals(oldStatus)) {

            // Đang giao
            if ("Đang giao".equals(status)) {

                jdbc.update("""
                    UPDATE dbo.fitfood_orders
                    SET status = N'Đang giao'
                    WHERE id = ?
                      AND status = N'Đang xử lý'
                    """, orderId);

            // Đã giao thành công
            } else if ("Đã giao".equals(status)) {

                jdbc.update("""
                    UPDATE dbo.fitfood_orders
                    SET status = N'Hoàn thành'
                    WHERE id = ?
                      AND status = N'Đang giao'
                    """, orderId);

            // Hủy trước khi giao hàng
            } else if ("Giao thất bại".equals(status)) {

                jdbc.update("""
                    UPDATE dbo.fitfood_orders
                    SET status = N'Đã hủy'
                    WHERE id = ?
                      AND status IN (
                          N'Chờ xác nhận',
                          N'Đang xử lý',
                          N'Đang giao'
                      )
                    """, orderId);
            }
        }

        redirect.addFlashAttribute(
                FLASH_SUCCESS,
                "Đã cập nhật thông tin giao hàng thành công."
        );

        return REDIRECT_DELIVERIES;
    }

    // ========================================
    // XÓA PHIẾU GIAO HÀNG
    // ========================================

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable long id,
            RedirectAttributes redirect) {

        int deleted = jdbc.update("""
            DELETE FROM dbo.fitfood_deliveries
            WHERE id = ?
              AND status IN (
                  N'Chờ giao',
                  N'Giao thất bại'
              )
            """, id);

        if (deleted == 0) {

            redirect.addFlashAttribute(
                    FLASH_ERROR,
                    "Không thể xóa: phiếu giao không tồn tại hoặc đang giao / đã giao."
            );

        } else {

            redirect.addFlashAttribute(
                    FLASH_SUCCESS,
                    "Đã xóa phiếu giao hàng."
            );
        }

        return REDIRECT_DELIVERIES;
    }

    // ========================================
    // DTO DANH SÁCH GIAO HÀNG
    // ========================================

    public record DeliveryRow(
            long id,
            long orderId,
            String receiverName,
            String receiverPhone,
            String deliveryAddress,
            String status,
            LocalDateTime sentAt,
            LocalDateTime deliveredAt,
            BigDecimal orderTotal,
            String accountName,
            String email,
            String orderStatus) {

        public List<String> allowedStatuses() {

            return allowedDeliveryStatuses(
                    status,
                    orderStatus
            );
        }
    }

    // ========================================
    // DTO DANH SÁCH ĐƠN CHƯA CÓ PHIẾU GIAO
    // ========================================

    public record OrderOption(
            long id,
            String customerName,
            String email) {
    }
}
