package com.fitfood.controller;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Trang quản trị "Đơn hàng" (/admin/orders).
 * Controller này chỉ đọc/ghi trực tiếp bằng JdbcTemplate (SQL thuần), KHÔNG dùng JPA/Entity,
 * vì đơn hàng cần JOIN nhiều bảng và lọc/tìm kiếm linh hoạt theo yêu cầu quản trị.
 *
 * Quyền truy cập: chỉ tài khoản có quyền SQL "QUAN_LY_DON_HANG" mới gọi được bất kỳ
 * method nào trong class này (được Spring Security kiểm tra qua @PreAuthorize ở cấp class,
 * dựa vào PermissionService).
 */
@Controller
@RequestMapping("/admin/orders")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_DON_HANG')")
public class OrderController {

    // Danh sách trạng thái hợp lệ của 1 đơn hàng, dùng để:
    // 1) đổ vào <select> trên giao diện (model "orderStatuses")
    // 2) validate giá trị status gửi lên từ form (VALID_STATUSES)
    private static final List<String> ORDER_STATUSES = List.of(
            "Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy");
    // Set để kiểm tra "status có hợp lệ không" nhanh hơn List (O(1) thay vì O(n))
    private static final Set<String> VALID_STATUSES = Set.copyOf(ORDER_STATUSES);

    // JdbcTemplate: lớp tiện ích của Spring để chạy SQL thuần (SELECT/UPDATE...)
    // mà không cần tự quản lý Connection, PreparedStatement, đóng/mở kết nối.
    private final JdbcTemplate jdbc;

    // Spring tự động "tiêm" (dependency injection) JdbcTemplate đã cấu hình sẵn
    // (kết nối tới SQL Server, xem application.properties) vào constructor này.
    public OrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET /admin/orders?keyword=...&filter=...
     * Hiển thị danh sách đơn hàng, có thể lọc theo trạng thái (filter)
     * và/hoặc tìm theo từ khóa (keyword: tên khách, email, hoặc mã đơn).
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "all") String filter,
                       Model model) {
        // Bỏ khoảng trắng dư ở đầu/cuối từ khóa tìm kiếm
        String normalizedKeyword = keyword.strip();
        // Nếu filter không nằm trong danh sách trạng thái hợp lệ -> coi như "all" (không lọc)
        // Tránh trường hợp người dùng sửa query string tay (?filter=abc) gây lỗi SQL hoặc lọt dữ liệu.
        String normalizedFilter = VALID_STATUSES.contains(filter) ? filter : "all";

        // Danh sách tham số sẽ truyền vào PreparedStatement theo đúng thứ tự dấu "?"
        // xuất hiện trong câu SQL được build động ở dưới.
        List<Object> parameters = new ArrayList<>();

        // Câu SQL cơ bản: lấy đơn hàng + tên/email khách hàng (JOIN users)
        // + đếm số dòng sản phẩm trong đơn (LEFT JOIN order_items rồi COUNT).
        // "WHERE 1 = 1" là mẹo thường dùng để các đoạn "AND ..." phía dưới
        // có thể nối vào tự do mà không cần tự xử lý trường hợp "WHERE đầu tiên hay là AND".
        StringBuilder sql = new StringBuilder("""
            SELECT o.id, o.placed_at, u.full_name, u.email, o.status, o.total,
                   COUNT(oi.id) AS item_count
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_order_items AS oi ON oi.order_id = o.id
            WHERE 1 = 1
            """);

        // Nếu có lọc theo trạng thái cụ thể (khác "all") thì thêm điều kiện AND o.status = ?
        if (!"all".equals(normalizedFilter)) {
            sql.append(" AND o.status = ?");
            parameters.add(normalizedFilter);
        }
        // Nếu có từ khóa tìm kiếm thì thêm điều kiện OR giữa 3 cột: tên, email, mã đơn.
        // Dùng "?" (PreparedStatement) để Spring tự escape giá trị -> chống SQL Injection.
        if (!normalizedKeyword.isEmpty()) {
            sql.append("""
                 AND (u.full_name LIKE ? OR u.email LIKE ?
                      OR CONVERT(VARCHAR(20), o.id) LIKE ?)
                """);
            // "%keyword%" để LIKE tìm khớp ở bất kỳ vị trí nào trong chuỗi
            String search = "%" + normalizedKeyword + "%";
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        }
        // GROUP BY vì có COUNT(oi.id); ORDER BY đơn mới nhất lên trước.
        sql.append("""
             GROUP BY o.id, o.placed_at, u.full_name, u.email, o.status, o.total
             ORDER BY o.placed_at DESC, o.id DESC
            """);

        // Thực thi câu SQL đã build, với tham số theo đúng thứ tự trong "parameters".
        // Lambda (rs, rowNum) -> ... là RowMapper: với MỖI dòng kết quả trả về từ SQL Server,
        // Spring sẽ gọi lambda này 1 lần để "map" dòng đó (ResultSet) thành 1 object Java (OrderRow).
        List<OrderRow> orders = jdbc.query(sql.toString(), (rs, rowNum) -> {
            // Timestamp (kiểu JDBC) -> LocalDateTime (kiểu Java hiện đại, dễ dùng ở Thymeleaf)
            Timestamp placedAt = rs.getTimestamp("placed_at");
            return new OrderRow(
                    rs.getLong("id"),
                    placedAt == null ? null : placedAt.toLocalDateTime(),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("status"),
                    rs.getBigDecimal("total"),
                    rs.getInt("item_count"));
        }, parameters.toArray());

        // Đưa dữ liệu vào Model để template Thymeleaf (admin-orders.html) đọc và render ra HTML.
        model.addAttribute("orders", orders);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("filter", normalizedFilter);
        model.addAttribute("orderStatuses", ORDER_STATUSES);
        return "admin-orders"; // tên file template (templates/admin-orders.html)
    }

    /**
     * POST /admin/orders/{id}/status
     * Cập nhật trạng thái của 1 đơn hàng (id lấy từ URL), rồi quay lại trang danh sách,
     * vẫn giữ nguyên bộ lọc/từ khóa đang xem (keyword, filter) để không bị mất vị trí tìm kiếm.
     */
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable long id,
                               @RequestParam String status,
                               @RequestParam(defaultValue = "") String keyword,
                               @RequestParam(defaultValue = "all") String filter,
                               RedirectAttributes redirect) {
        // Validate: status gửi lên phải là 1 trong 5 giá trị ở ORDER_STATUSES.
        // (Phòng trường hợp request bị sửa tay / gửi sai từ bên ngoài form.)
        if (!VALID_STATUSES.contains(status)) {
            // addFlashAttribute: lưu thông báo tạm, chỉ hiển thị được ở request redirect
            // TIẾP THEO (sau khi chuyển trang), rồi tự xoá - tránh thông báo bị lặp lại khi F5.
            redirect.addFlashAttribute("error", "Trạng thái đơn hàng không hợp lệ.");
        } else {
            // UPDATE đơn giản theo id. jdbc.update() trả về số dòng bị ảnh hưởng.
            int updated = jdbc.update(
                    "UPDATE dbo.fitfood_orders SET status = ? WHERE id = ?", status, id);
            if (updated == 0) {
                // updated == 0 nghĩa là không có đơn hàng nào khớp id này (đã bị xoá / sai id).
                redirect.addFlashAttribute("error", "Không tìm thấy đơn hàng cần cập nhật.");
            } else {
                redirect.addFlashAttribute("success", "Đã cập nhật trạng thái đơn hàng.");
            }
        }
        // addAttribute (khác addFlashAttribute): gắn vào QUERY STRING của URL redirect,
        // ví dụ redirect:/admin/orders?keyword=...&filter=... để trang danh sách sau khi
        // load lại vẫn đang ở đúng bộ lọc/tìm kiếm trước khi bấm "Lưu".
        redirect.addAttribute("keyword", keyword);
        redirect.addAttribute("filter", filter);
        return "redirect:/admin/orders";
    }

    /**
     * DTO (Data Transfer Object) đại diện cho 1 dòng trong bảng danh sách đơn hàng.
     * Đây KHÔNG phải Entity/JPA - chỉ là object thuần Java để mang dữ liệu từ
     * RowMapper (trong method list()) ra tới template Thymeleaf.
     *
     * Dùng "record" (Java 17+): tự sinh constructor + accessor (id(), placedAt(), ...) +
     * equals/hashCode/toString, không cần viết tay. Thymeleaf hiểu ${order.id}, ${order.placedAt}
     * và tự gọi đúng accessor tương ứng, nên template HTML không cần thay đổi gì.
     */
    public record OrderRow(long id, LocalDateTime placedAt, String customerName,
                           String email, String status, BigDecimal total, int itemCount) {
    }
}
