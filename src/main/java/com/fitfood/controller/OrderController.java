package com.fitfood.controller;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
    private boolean canUpdateStatus(String currentStatus, String newStatus) {

    if (currentStatus == null || newStatus == null) {
        return false;
    }

    return switch (currentStatus) {

        case "Chờ xác nhận" ->
            "Đang xử lý".equals(newStatus)
            || "Đã hủy".equals(newStatus);

        case "Đang xử lý" ->
            "Đang giao".equals(newStatus)
            || "Đã hủy".equals(newStatus);

        case "Đang giao" ->
            "Hoàn thành".equals(newStatus);

        case "Hoàn thành", "Đã hủy" ->
            false;

        default -> false;
    };
}
    // Chỉ đơn ở các trạng thái này mới được xóa (xem method delete()).
    private static final Set<String> DELETABLE_STATUSES =
        Set.of("Chờ xác nhận", "Đã hủy", "Hoàn thành");

    // Gom các chuỗi lặp lại nhiều lần thành hằng số (giống DeliveryController).
    private static final String FLASH_ERROR = "error";
    private static final String FLASH_SUCCESS = "success";
    private static final String PARAM_KEYWORD = "keyword";
    private static final String PARAM_FILTER = "filter";
    private static final String REDIRECT_ORDERS = "redirect:/admin/orders";

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
                   @RequestParam(defaultValue = "1") int page,
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
            SELECT o.id, o.placed_at, u.full_name, u.email,
                   COALESCE(d.receiver_phone, cp.phone) AS phone,
                   COALESCE(d.delivery_address, cp.address) AS address,
                   o.status, o.total,
                   COALESCE((
                       SELECT TOP 1 pm.status
                       FROM dbo.fitfood_payments pm
                       WHERE pm.order_id = o.id
                       ORDER BY pm.id DESC
                   ), N'Chưa thanh toán') AS payment_status,
                   COUNT(oi.id) AS item_count
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_deliveries AS d ON d.order_id = o.id
            LEFT JOIN dbo.fitfood_customer_profiles AS cp ON cp.user_id = u.id
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
                      OR d.receiver_phone LIKE ? OR cp.phone LIKE ?
                      OR d.delivery_address LIKE ? OR cp.address LIKE ?
                      OR CONVERT(VARCHAR(20), o.id) LIKE ?)
                """);
            // "%keyword%" để LIKE tìm khớp ở bất kỳ vị trí nào trong chuỗi
            String search = "%" + normalizedKeyword + "%";
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        }
        // GROUP BY vì có COUNT(oi.id); ORDER BY đơn mới nhất lên trước.
        sql.append("""
             GROUP BY o.id, o.placed_at, u.full_name, u.email,
                      d.receiver_phone, cp.phone, d.delivery_address, cp.address,
                      o.status, o.total
             ORDER BY o.placed_at ASC, o.id ASC
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
                    rs.getString("phone"),
                    rs.getString("address"),
                    rs.getString("status"),
                    rs.getString("payment_status"),
                    rs.getBigDecimal("total"),
                    rs.getInt("item_count"));
        }, parameters.toArray());

        // Đưa dữ liệu vào Model để template Thymeleaf (admin-orders.html) đọc và render ra HTML.
        // Số đơn hàng trên mỗi trang
        int pageSize = 10;

        // Tổng số đơn hàng sau khi tìm kiếm và lọc
        int totalOrders = orders.size();

        // Tính tổng số trang
        int totalPages = Math.max(
                1,
                (int) Math.ceil((double) totalOrders / pageSize)
        );

        // Đảm bảo số trang hợp lệ
        page = Math.max(1, Math.min(page, totalPages));

        // Xác định vị trí bắt đầu và kết thúc
        int start = (page - 1) * pageSize;

        int end = Math.min(start + pageSize, totalOrders);

        // Lấy danh sách đơn hàng của trang hiện tại
        List<OrderRow> paginatedOrders = orders.subList(start, end);

        // Truyền dữ liệu sang giao diện
        model.addAttribute("orders", paginatedOrders);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute(PARAM_KEYWORD, normalizedKeyword);
        model.addAttribute(PARAM_FILTER, normalizedFilter);
        model.addAttribute("orderStatuses", ORDER_STATUSES);
        return "admin-orders"; // tên file template (templates/admin-orders.html)
    }

    /**
     * POST /admin/orders/{id}/status
     * Cập nhật trạng thái của 1 đơn hàng (id lấy từ URL), rồi quay lại trang danh sách,
     * vẫn giữ nguyên bộ lọc/từ khóa đang xem (keyword, filter) để không bị mất vị trí tìm kiếm.
     */
 
@GetMapping("/{id}")
public String detail(@PathVariable long id, Model model) {

    String sql = """
        SELECT o.id, o.placed_at, u.full_name, u.email,
               COALESCE(d.receiver_phone, cp.phone) AS phone,
               COALESCE(d.delivery_address, cp.address) AS address,
               o.status, o.note,
               o.subtotal, o.discount, o.delivery_fee, o.total,
               p.code AS promotion_code,
               COALESCE((
                   SELECT TOP 1 pm.status
                   FROM dbo.fitfood_payments pm
                   WHERE pm.order_id = o.id
                   ORDER BY pm.id DESC
               ), N'Chưa thanh toán') AS payment_status
        FROM dbo.fitfood_orders o
        JOIN dbo.fitfood_users u ON u.id = o.user_id
        LEFT JOIN dbo.fitfood_deliveries d ON d.order_id = o.id
        LEFT JOIN dbo.fitfood_customer_profiles cp ON cp.user_id = u.id
        LEFT JOIN dbo.fitfood_promotions p ON p.id = o.promotion_id
        WHERE o.id = ?
        """;

    RowMapper<Map<String, Object>> mapper = (rs, row) -> {
        Map<String, Object> m = new LinkedHashMap<>();

        Timestamp ts = rs.getTimestamp("placed_at");

        m.put("id", rs.getLong("id"));
        m.put("placedAt", ts == null ? null : ts.toLocalDateTime());
        m.put("customerName", rs.getString("full_name"));
        m.put("email", rs.getString("email"));
        m.put("phone", rs.getString("phone"));
        m.put("address", rs.getString("address"));
        m.put("status", rs.getString("status"));
        m.put("paymentStatus", rs.getString("payment_status"));
        m.put("note", rs.getString("note") == null ? "" : rs.getString("note"));
        m.put("subtotal", rs.getBigDecimal("subtotal"));
        m.put("discount", rs.getBigDecimal("discount"));
        m.put("deliveryFee", rs.getBigDecimal("delivery_fee"));
        m.put("total", rs.getBigDecimal("total"));
        m.put("promotionCode", rs.getString("promotion_code") == null
                ? "Không áp dụng" : rs.getString("promotion_code"));

        return m;
    };

    List<Map<String, Object>> orders =
            jdbc.query(sql, mapper, id);

    if (orders.isEmpty()) {
        return "redirect:/admin/orders";
    }

    List<Map<String, Object>> items = jdbc.query("""
        SELECT product_name, quantity, unit_price, line_total
        FROM dbo.fitfood_order_items
        WHERE order_id = ?
        ORDER BY id
        """, (rs, row) -> {

        Map<String, Object> item = new LinkedHashMap<>();

        item.put("productName", rs.getString("product_name"));
        item.put("quantity", rs.getInt("quantity"));
        item.put("unitPrice", rs.getBigDecimal("unit_price"));
        item.put("lineTotal", rs.getBigDecimal("line_total"));

        return item;

    }, id);

    model.addAttribute("invoice", orders.get(0));
    model.addAttribute("items", items);

    return "admin-invoice-detail";
}
    
@PostMapping("/{id}/status")
@Transactional
public String updateStatus(
        @PathVariable long id,
        @RequestParam String status,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "all") String filter,
        RedirectAttributes redirect) {

    // Giữ nguyên bộ lọc và từ khóa tìm kiếm
    redirect.addAttribute(PARAM_KEYWORD, keyword);
    redirect.addAttribute(PARAM_FILTER, filter);

    // Kiểm tra trạng thái gửi lên
    if (!VALID_STATUSES.contains(status)) {
        redirect.addFlashAttribute(
                FLASH_ERROR,
                "Trạng thái đơn hàng không hợp lệ."
        );

        return REDIRECT_ORDERS;
    }

    // Lấy trạng thái hiện tại của đơn hàng
    List<String> currentStatuses = jdbc.query("""
        SELECT status
        FROM dbo.fitfood_orders WITH (UPDLOCK, HOLDLOCK)
        WHERE id = ?
        """,
        (rs, rowNum) -> rs.getString("status"),
        id
    );

    // Kiểm tra đơn hàng có tồn tại không
    if (currentStatuses.isEmpty()) {
        redirect.addFlashAttribute(
                FLASH_ERROR,
                "Không tìm thấy đơn hàng."
        );

        return REDIRECT_ORDERS;
    }

    String currentStatus = currentStatuses.get(0);

    // Kiểm tra trạng thái có được phép thay đổi không
    if (!canUpdateStatus(currentStatus, status)) {
        redirect.addFlashAttribute(
                FLASH_ERROR,
                "Không thể chuyển trạng thái từ "
                + currentStatus + " sang " + status + "."
        );

        return REDIRECT_ORDERS;
    }

    // Cập nhật trạng thái đơn hàng
    jdbc.update("""
        UPDATE dbo.fitfood_orders
        SET status = ?
        WHERE id = ?
        """, status, id);

    // Đồng bộ trạng thái giao hàng
    if ("Đang giao".equals(status)) {

        jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET status = N'Đang giao',
                sent_at = COALESCE(sent_at, SYSDATETIME())
            WHERE order_id = ?
              AND status <> N'Đã giao'
            """, id);

    } else if ("Hoàn thành".equals(status)) {

        jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET status = N'Đã giao',
                sent_at = COALESCE(sent_at, SYSDATETIME()),
                delivered_at = COALESCE(
                    delivered_at, SYSDATETIME()
                )
            WHERE order_id = ?
            """, id);

    } else if ("Đã hủy".equals(status)) {

        jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET status = N'Giao thất bại'
            WHERE order_id = ?
              AND status <> N'Đã giao'
            """, id);

    } else if ("Đang xử lý".equals(status)) {

        jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET status = N'Chờ giao',
                delivered_at = NULL
            WHERE order_id = ?
              AND status NOT IN (
                  N'Đã giao', N'Giao thất bại'
              )
            """, id);
    }

    redirect.addFlashAttribute(
            FLASH_SUCCESS,
            "Cập nhật trạng thái đơn hàng thành công."
    );

    return REDIRECT_ORDERS;
}


    /**
     * POST /admin/orders/{id}/delete
     * Xóa hẳn 1 đơn hàng khỏi database, kèm các dòng con phụ thuộc vào nó.
     *
     * Chỉ cho xóa khi đơn "chưa phát sinh nghiệp vụ thật":
     *   - trạng thái là "Chờ xác nhận" hoặc "Đã hủy" (DELETABLE_STATUSES)
     *   - chưa xuất hóa đơn (fitfood_invoices)
     *   - chưa có khoản thanh toán nào "Đã thanh toán" (fitfood_payments)
     *   - phiếu giao (nếu có) chưa ở trạng thái "Đang giao" / "Đã giao"
     * Các đơn còn lại phải giữ lại để không làm sai báo cáo doanh thu / lịch sử giao hàng.
     *
     * @Transactional: toàn bộ các câu DELETE bên dưới chạy trong CÙNG 1 transaction -
     * nếu 1 câu lỗi thì tất cả được rollback, không bao giờ xảy ra cảnh xóa được
     * order_items mà đơn hàng vẫn còn (dữ liệu "mồ côi").
     */
    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable long id,
                         @RequestParam(defaultValue = "") String keyword,
                         @RequestParam(defaultValue = "all") String filter,
                         RedirectAttributes redirect) {
        // Đọc trạng thái đơn + đếm các dữ liệu liên quan trong 1 câu SQL.
        // WITH (UPDLOCK, HOLDLOCK): khóa dòng đơn hàng tới hết transaction, để trong lúc
        // đang kiểm tra/xóa thì request khác (ví dụ đổi trạng thái sang "Đang giao")
        // không chen vào làm điều kiện kiểm tra bị sai.
        List<OrderDeleteCheck> checks = jdbc.query("""
            SELECT o.status,
                   (SELECT COUNT(*) FROM dbo.fitfood_invoices AS i
                    WHERE i.order_id = o.id) AS invoice_count,
                   (SELECT COUNT(*) FROM dbo.fitfood_payments AS p
                    WHERE p.order_id = o.id AND p.status = N'Đã thanh toán') AS paid_count,
                   (SELECT COUNT(*) FROM dbo.fitfood_deliveries AS d
                    WHERE d.order_id = o.id AND d.status IN (N'Đang giao', N'Đã giao')) AS shipping_count
            FROM dbo.fitfood_orders AS o WITH (UPDLOCK, HOLDLOCK)
            WHERE o.id = ?
            """, (rs, rowNum) -> new OrderDeleteCheck(
                rs.getString("status"),
                rs.getInt("invoice_count"),
                rs.getInt("paid_count"),
                rs.getInt("shipping_count")), id);

        // Giữ lại bộ lọc/từ khóa khi quay về danh sách (giống updateStatus).
        redirect.addAttribute(PARAM_KEYWORD, keyword);
        redirect.addAttribute(PARAM_FILTER, filter);

        // Lần lượt kiểm tra từng điều kiện; gặp điều kiện sai đầu tiên thì báo lỗi và dừng.
        String error = null;
        if (checks.isEmpty()) {
            error = "Không tìm thấy đơn hàng cần xóa.";
        } else if (!DELETABLE_STATUSES.contains(checks.get(0).status())) {
            error = "Chỉ được xóa đơn hàng ở trạng thái Chờ xác nhận, Đã hủy hoặc Hoàn thành.";
        } else if (checks.get(0).invoiceCount() > 0) {
            error = "Không thể xóa: đơn hàng đã được xuất hóa đơn.";
        } else if (checks.get(0).paidCount() > 0
        && !"Đã hủy".equals(checks.get(0).status())) {
            error = "Đơn hàng đã thanh toán phải được hủy trước khi xóa.";
        } else if (checks.get(0).shippingCount() > 0) {
            error = "Không thể xóa: phiếu giao của đơn hàng đang giao hoặc đã giao.";
        }
        if (error != null) {
            redirect.addFlashAttribute(FLASH_ERROR, error);
            return REDIRECT_ORDERS;
        }

        try {
            // Xóa bảng con trước, bảng cha (fitfood_orders) sau cùng - vì các khóa ngoại
            // FK_ff_delivery_order, FK_ff_payment_order, FK_ff_orderitem_order không có
            // ON DELETE CASCADE, xóa đơn trước sẽ bị SQL Server chặn.
            jdbc.update("DELETE FROM dbo.fitfood_deliveries WHERE order_id = ?", id);
            jdbc.update("DELETE FROM dbo.fitfood_payments WHERE order_id = ?", id);
            jdbc.update("DELETE FROM dbo.fitfood_order_items WHERE order_id = ?", id);
            jdbc.update("DELETE FROM dbo.fitfood_orders WHERE id = ?", id);
            redirect.addFlashAttribute(FLASH_SUCCESS, "Đã xóa đơn hàng #" + id + ".");
        } catch (DataIntegrityViolationException ex) {
            // Còn bảng khác (thêm sau này) tham chiếu tới đơn hàng -> SQL Server chặn xóa.
            // Vì exception đã bị bắt ở đây, Spring sẽ KHÔNG tự rollback, nên phải đánh dấu
            // rollback thủ công để hoàn tác các câu DELETE bảng con đã chạy ở trên.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            redirect.addFlashAttribute(FLASH_ERROR,
                    "Không thể xóa: đơn hàng đang được dữ liệu khác sử dụng.");
        }
        return REDIRECT_ORDERS;
    }

    /**
     * Kết quả kiểm tra trước khi xóa đơn hàng (dùng riêng trong method delete()).
     */
    private record OrderDeleteCheck(String status, int invoiceCount, int paidCount, int shippingCount) {
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
    public record OrderRow(
        long id,
        LocalDateTime placedAt,
        String customerName,
        String email,
        String phone,
        String address,
        String status,
        String paymentStatus,
        BigDecimal total,
        int itemCount) {
}



}
