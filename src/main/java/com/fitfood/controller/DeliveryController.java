package com.fitfood.controller;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Trang quản trị "Giao hàng" (/admin/deliveries).
 * Mỗi đơn hàng (fitfood_orders) có thể có TỐI ĐA 1 "phiếu giao hàng" (fitfood_deliveries) -
 * ràng buộc này được đảm bảo ở 2 lớp: UNIQUE(order_id) trong database, và điều kiện
 * NOT EXISTS khi INSERT ở method create() bên dưới.
 *
 * Giống OrderController: dùng JdbcTemplate (SQL thuần) thay vì JPA, và yêu cầu quyền
 * SQL "QUAN_LY_GIAO_HANG" cho mọi endpoint trong class này.
 */
@Controller
@RequestMapping("/admin/deliveries")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_GIAO_HANG')")
public class DeliveryController {
    // 4 trạng thái của 1 phiếu giao hàng, theo đúng thứ tự vòng đời:
    // Chờ giao -> Đang giao -> Đã giao (hoặc Giao thất bại nếu giao không thành công).
    private static final List<String> DELIVERY_STATUSES =
            List.of("Chờ giao", "Đang giao", "Đã giao", "Giao thất bại");
    private static final Set<String> VALID_STATUSES = Set.copyOf(DELIVERY_STATUSES);

    // Gom các chuỗi lặp lại nhiều lần (key flash message, đường dẫn redirect) thành hằng số,
    // để nếu sau này cần đổi tên key hoặc đường dẫn thì chỉ sửa 1 chỗ, không phải tìm từng dòng.
    private static final String FLASH_ERROR = "error";
    private static final String FLASH_SUCCESS = "success";
    private static final String REDIRECT_DELIVERIES = "redirect:/admin/deliveries";

    private final JdbcTemplate jdbc;

    public DeliveryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET /admin/deliveries?keyword=...&filter=...
     * Hiển thị:
     *  1) Danh sách các phiếu giao hàng hiện có (có thể lọc theo trạng thái / tìm theo từ khóa).
     *  2) Danh sách các đơn hàng CHƯA có phiếu giao (để đổ vào <select> ở form "Tạo phiếu").
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "all") String filter,
                       Model model) {
        String normalizedKeyword = keyword.strip();
        String normalizedFilter = VALID_STATUSES.contains(filter) ? filter : "all";

        // Câu SQL dùng "?" dạng tham số cố định (không build động từng đoạn như OrderController)
        // vì số điều kiện lọc ở đây ít hơn và không thay đổi cấu trúc.
        // Mẹo "(? = 'all' OR d.status = ?)": nếu truyền normalizedFilter = 'all' thì điều kiện
        // luôn đúng (không lọc); ngược lại chỉ so khớp đúng status đó. Tương tự cho đoạn
        // "(? = '' OR ... LIKE ?)" bên dưới dùng cho từ khóa tìm kiếm.
        String sql = """
            SELECT d.id, d.order_id, d.receiver_name, d.receiver_phone,
                   d.delivery_address, d.status, d.sent_at, d.delivered_at,
                   o.total, u.full_name AS account_name, u.email
            FROM dbo.fitfood_deliveries AS d
            JOIN dbo.fitfood_orders AS o ON o.id = d.order_id
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            WHERE (? = 'all' OR d.status = ?)
              AND (? = '' OR d.receiver_name LIKE ? OR d.receiver_phone LIKE ?
                   OR d.delivery_address LIKE ? OR u.email LIKE ?
                   OR CONVERT(VARCHAR(20), d.order_id) LIKE ?)
            ORDER BY CASE WHEN d.sent_at IS NULL THEN 0 ELSE 1 END,
                     d.sent_at DESC, d.id DESC
            """;
        // "%keyword%" để LIKE khớp bất kỳ vị trí nào trong chuỗi.
        String search = "%" + normalizedKeyword + "%";
        // RowMapper: chuyển từng dòng ResultSet thành 1 object DeliveryRow.
        List<DeliveryRow> deliveries = jdbc.query(sql, (rs, rowNum) -> {
            Timestamp sentAt = rs.getTimestamp("sent_at");
            Timestamp deliveredAt = rs.getTimestamp("delivered_at");
            return new DeliveryRow(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getString("receiver_name"),
                    rs.getString("receiver_phone"),
                    rs.getString("delivery_address"),
                    rs.getString("status"),
                    // Chỉ convert Timestamp -> LocalDateTime khi khác null, tránh NullPointerException
                    sentAt == null ? null : sentAt.toLocalDateTime(),
                    deliveredAt == null ? null : deliveredAt.toLocalDateTime(),
                    rs.getBigDecimal("total"),
                    rs.getString("account_name"),
                    rs.getString("email"));
            // Thứ tự tham số dưới đây PHẢI khớp đúng thứ tự dấu "?" ở trên:
            // (filter, filter) cho điều kiện trạng thái, rồi (keyword, search x5) cho tìm kiếm.
        }, normalizedFilter, normalizedFilter, normalizedKeyword, search, search, search, search, search);

        // Lấy các đơn hàng CHƯA có phiếu giao và CHƯA bị hủy, để hiển thị trong <select> tạo phiếu.
        // LEFT JOIN + "d.id IS NULL" là cách SQL chuẩn để tìm "đơn hàng không khớp với bất kỳ
        // phiếu giao nào" (tức là chưa từng tạo phiếu).
        List<OrderOption> ordersWithoutDelivery = jdbc.query("""
            SELECT o.id, u.full_name, u.email
            FROM dbo.fitfood_orders AS o
            JOIN dbo.fitfood_users AS u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_deliveries AS d ON d.order_id = o.id
            WHERE d.id IS NULL AND o.status <> N'Đã hủy'
            ORDER BY o.placed_at DESC, o.id DESC
            """, (rs, rowNum) -> new OrderOption(
                rs.getLong("id"), rs.getString("full_name"), rs.getString("email")));

        model.addAttribute("deliveries", deliveries);
        model.addAttribute("ordersWithoutDelivery", ordersWithoutDelivery);
        model.addAttribute("deliveryStatuses", DELIVERY_STATUSES);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("filter", normalizedFilter);
        return "admin-deliveries";
    }

    /**
     * POST /admin/deliveries
     * Tạo phiếu giao hàng mới cho 1 đơn hàng (orderId), kèm thông tin người nhận.
     */
    @PostMapping
    public String create(@RequestParam long orderId,
                         @RequestParam String receiverName,
                         @RequestParam String receiverPhone,
                         @RequestParam String deliveryAddress,
                         RedirectAttributes redirect) {
        // Chuẩn hoá dữ liệu: bỏ khoảng trắng dư ở đầu/cuối.
        String name = receiverName.strip();
        String phone = receiverPhone.strip();
        String address = deliveryAddress.strip();
        // Validate độ dài/rỗng khớp với giới hạn cột trong database
        // (receiver_name NVARCHAR(100), receiver_phone VARCHAR(20), delivery_address NVARCHAR(255)) -
        // kiểm tra trước ở Java để trả thông báo lỗi thân thiện, tránh để SQL Server tự ném lỗi.
        if (name.isEmpty() || name.length() > 100
                || phone.isEmpty() || phone.length() > 20
                || address.isEmpty() || address.length() > 255) {
            redirect.addFlashAttribute(FLASH_ERROR,
                    "Vui lòng nhập tên, số điện thoại và địa chỉ hợp lệ.");
            return REDIRECT_DELIVERIES;
        }

        try {
            // INSERT ... SELECT: chỉ thực sự chèn dòng mới nếu đơn hàng orderId
            //   - tồn tại (SELECT ... FROM fitfood_orders WHERE o.id = ?)
            //   - chưa bị hủy (status <> 'Đã hủy')
            //   - và CHƯA có phiếu giao nào (NOT EXISTS subquery).
            // Nếu 1 trong 3 điều kiện sai thì SELECT không trả dòng nào -> INSERT 0 dòng,
            // thay vì phải query kiểm tra riêng rồi mới INSERT (gộp thành 1 câu lệnh, tránh
            // race condition giữa 2 request tạo phiếu cùng lúc cho 1 đơn).
            int inserted = jdbc.update("""
                INSERT INTO dbo.fitfood_deliveries
                    (order_id, receiver_name, receiver_phone, delivery_address)
                SELECT o.id, ?, ?, ?
                FROM dbo.fitfood_orders AS o
                WHERE o.id = ?
                  AND o.status <> N'Đã hủy'
                  AND NOT EXISTS (
                      SELECT 1 FROM dbo.fitfood_deliveries AS d WHERE d.order_id = o.id
                  )
                """, name, phone, address, orderId);
            if (inserted == 0) {
                redirect.addFlashAttribute(FLASH_ERROR,
                        "Không thể tạo phiếu: đơn hàng không tồn tại, đã hủy hoặc đã có phiếu giao.");
            } else {
                redirect.addFlashAttribute(FLASH_SUCCESS, "Đã tạo phiếu giao hàng.");
            }
        } catch (DataIntegrityViolationException ex) {
            // Lưới an toàn thứ 2: nếu 2 request chạy đúng lúc "lọt" qua điều kiện NOT EXISTS
            // ở trên (hiếm, do race condition), ràng buộc UNIQUE(order_id) ở database sẽ
            // chặn request thứ 2 và ném exception này - ta bắt lại để báo lỗi thân thiện
            // thay vì để lỗi 500 hiển thị cho người dùng.
            redirect.addFlashAttribute(FLASH_ERROR,
                    "Đơn hàng đã có phiếu giao hoặc dữ liệu không hợp lệ.");
        }
        return REDIRECT_DELIVERIES;
    }

    /**
     * POST /admin/deliveries/{id}/update
     * Cập nhật thông tin người nhận + trạng thái của 1 phiếu giao hàng (id trong URL).
     * Đây cũng là nơi tự động ghi nhận thời điểm "gửi đi" (sent_at) và "giao xong" (delivered_at).
     */
    @PostMapping("/{id}/update")
    public String update(@PathVariable long id,
                         @RequestParam String status,
                         @RequestParam String receiverName,
                         @RequestParam String receiverPhone,
                         @RequestParam String deliveryAddress,
                         RedirectAttributes redirect) {
        String name = receiverName.strip();
        String phone = receiverPhone.strip();
        String address = deliveryAddress.strip();
        // Validate: status phải thuộc DELIVERY_STATUSES, và tên/SĐT/địa chỉ không rỗng,
        // không vượt độ dài cột trong database.
        if (!VALID_STATUSES.contains(status)
                || name.isEmpty() || name.length() > 100
                || phone.isEmpty() || phone.length() > 20
                || address.isEmpty() || address.length() > 255) {
            redirect.addFlashAttribute(FLASH_ERROR,
                    "Trạng thái, tên người nhận, số điện thoại hoặc địa chỉ không hợp lệ.");
            return REDIRECT_DELIVERIES;
        }

        // Luôn cập nhật tên/SĐT/địa chỉ + trạng thái. Hai cột sent_at/delivered_at được
        // tính bằng CASE ngay trong SQL (không cần đọc dữ liệu cũ ra Java rồi tính lại):
        //   - sent_at: CHỈ set = thời điểm hiện tại (SYSDATETIME()) khi status mới là
        //     "Đang giao" VÀ sent_at hiện đang NULL (nghĩa là lần đầu chuyển sang "Đang giao" -
        //     nếu đã có sent_at từ trước, giữ nguyên, không bị ghi đè khi sửa lại các lần sau).
        //   - delivered_at: set = thời điểm hiện tại khi status mới là "Đã giao" và chưa từng
        //     set; NHƯNG nếu status đổi sang trạng thái KHÁC "Đã giao" (ví dụ sửa lại thành
        //     "Đang giao" hoặc "Giao thất bại") thì delivered_at bị xóa (NULL) - vì đơn
        //     thực tế không còn được coi là "đã giao xong" nữa.
        int updated = jdbc.update("""
            UPDATE dbo.fitfood_deliveries
            SET receiver_name = ?,
                receiver_phone = ?,
                delivery_address = ?,
                status = ?,
                sent_at = CASE
                    WHEN ? = N'Đang giao' AND sent_at IS NULL THEN SYSDATETIME()
                    ELSE sent_at
                END,
                delivered_at = CASE
                    WHEN ? = N'Đã giao' AND delivered_at IS NULL THEN SYSDATETIME()
                    WHEN ? <> N'Đã giao' THEN NULL
                    ELSE delivered_at
                END
            WHERE id = ?
            """, name, phone, address, status, status, status, status, id);
        if (updated == 0) {
            redirect.addFlashAttribute(FLASH_ERROR, "Không tìm thấy phiếu giao hàng cần cập nhật.");
        } else {
            redirect.addFlashAttribute(FLASH_SUCCESS, "Đã cập nhật thông tin giao hàng.");
        }
        return REDIRECT_DELIVERIES;
    }

    /**
     * DTO cho 1 dòng trong bảng "Danh sách giao hàng" (template admin-deliveries.html).
     * Gộp dữ liệu từ 3 bảng: fitfood_deliveries, fitfood_orders (total), fitfood_users
     * (accountName/email của người ĐẶT đơn - khác với receiverName là người NHẬN hàng,
     * vì khách có thể đặt giao cho người khác).
     *
     * Dùng "record" (Java 17+) thay cho class thường: record tự sinh constructor, getter
     * (gọi là "accessor", ví dụ orderTotal() thay vì getOrderTotal()), equals/hashCode/toString -
     * không cần viết tay như class cũ. Thymeleaf hiểu ${delivery.orderTotal} và tự gọi
     * phương thức orderTotal() của record, nên template HTML KHÔNG cần sửa gì.
     * (11 tham số là hơi nhiều, nhưng vì đây chỉ là "bản sao phẳng" của 1 dòng kết quả SQL
     * JOIN 3 bảng nên vẫn hợp lý hơn là tách nhỏ thành nhiều record con cho 1 màn hình đơn giản.)
     */
    public record DeliveryRow(long id, long orderId, String receiverName, String receiverPhone,
                               String deliveryAddress, String status, LocalDateTime sentAt,
                               LocalDateTime deliveredAt, java.math.BigDecimal orderTotal,
                               String accountName, String email) {
    }

    /**
     * DTO cho 1 option trong <select> "Đơn hàng" ở form tạo phiếu giao -
     * chỉ cần id + tên/email khách để hiển thị, chưa cần đầy đủ thông tin đơn hàng.
     */
    public record OrderOption(long id, String customerName, String email) {
    }
}
