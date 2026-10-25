package com.fitfood.controller;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Hóa đơn hiển thị dữ liệu trực tiếp từ đơn hàng hiện có. */
@Controller
@RequestMapping("/admin/invoices")
@PreAuthorize("@permissionService.allowed(authentication, 'HOA_DON')")
public class InvoiceController {
    private final JdbcTemplate jdbc;
    public InvoiceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "all") String filter,
                       Model model) {
        String q = keyword == null ? "" : keyword.strip();
        String status = filter == null ? "all" : filter.strip();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT o.id, o.placed_at, u.full_name, u.email, o.status,
                   o.subtotal, o.discount, o.delivery_fee, o.total,
                   COUNT(oi.id) AS item_count
            FROM dbo.fitfood_orders o
            JOIN dbo.fitfood_users u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_order_items oi ON oi.order_id = o.id
            WHERE 1=1
            """);
        if (!"all".equalsIgnoreCase(status)) { sql.append(" AND o.status = ?"); args.add(status); }
        if (!q.isEmpty()) {
            sql.append(" AND (u.full_name LIKE ? OR u.email LIKE ? OR CONVERT(VARCHAR(20), o.id) LIKE ?)");
            String like = "%" + q + "%"; args.add(like); args.add(like); args.add(like);
        }
        sql.append("""
            GROUP BY o.id, o.placed_at, u.full_name, u.email, o.status,
                     o.subtotal, o.discount, o.delivery_fee, o.total
            ORDER BY o.placed_at DESC, o.id DESC
            """);
        List<Map<String,Object>> invoices = jdbc.query(sql.toString(), args.toArray(), (rs, row) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            Timestamp ts = rs.getTimestamp("placed_at");
            m.put("id", rs.getLong("id")); m.put("placedAt", ts == null ? null : ts.toLocalDateTime());
            m.put("customerName", or(rs.getString("full_name"), "Khách hàng"));
            m.put("email", or(rs.getString("email"), "")); m.put("status", or(rs.getString("status"), ""));
            m.put("subtotal", money(rs.getBigDecimal("subtotal"))); m.put("discount", money(rs.getBigDecimal("discount")));
            m.put("deliveryFee", money(rs.getBigDecimal("delivery_fee"))); m.put("total", money(rs.getBigDecimal("total")));
            m.put("itemCount", rs.getInt("item_count")); return m;
        });
        model.addAttribute("invoices", invoices); model.addAttribute("keyword", q); model.addAttribute("filter", status);
        model.addAttribute("statuses", List.of("Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy"));
        return "admin-invoices";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        String headerSql = """
            SELECT o.id, o.placed_at, u.full_name, u.email, o.status, o.note,
                   o.subtotal, o.discount, o.delivery_fee, o.total, p.code AS promotion_code
            FROM dbo.fitfood_orders o
            JOIN dbo.fitfood_users u ON u.id = o.user_id
            LEFT JOIN dbo.fitfood_promotions p ON p.id = o.promotion_id
            WHERE o.id = ?
            """;
        RowMapper<Map<String,Object>> headerMapper = (rs, row) -> {
            Map<String,Object> m = new LinkedHashMap<>(); Timestamp ts = rs.getTimestamp("placed_at");
            m.put("id", rs.getLong("id")); m.put("placedAt", ts == null ? null : ts.toLocalDateTime());
            m.put("customerName", or(rs.getString("full_name"), "Khách hàng")); m.put("email", or(rs.getString("email"), ""));
            m.put("status", or(rs.getString("status"), "")); m.put("note", or(rs.getString("note"), ""));
            m.put("subtotal", money(rs.getBigDecimal("subtotal"))); m.put("discount", money(rs.getBigDecimal("discount")));
            m.put("deliveryFee", money(rs.getBigDecimal("delivery_fee"))); m.put("total", money(rs.getBigDecimal("total")));
            m.put("promotionCode", or(rs.getString("promotion_code"), "Không áp dụng")); return m;
        };
        // Dùng PreparedStatement để SQL Server nhận đúng tham số thay cho dấu ?.
        List<Map<String,Object>> headers = jdbc.query(headerSql, ps -> ps.setLong(1, id), headerMapper);
        if (headers.isEmpty()) return "redirect:/admin/invoices";
        List<Map<String,Object>> items = jdbc.query("""
            SELECT product_name, quantity, unit_price, line_total
            FROM dbo.fitfood_order_items WHERE order_id = ? ORDER BY id
            """, ps -> ps.setLong(1, id), (rs, row) -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("productName", or(rs.getString("product_name"), "Sản phẩm"));
                m.put("quantity", rs.getInt("quantity")); m.put("unitPrice", money(rs.getBigDecimal("unit_price")));
                m.put("lineTotal", money(rs.getBigDecimal("line_total"))); return m;
            });
        model.addAttribute("invoice", headers.get(0)); model.addAttribute("items", items);
        return "admin-invoice-detail";
    }
    private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String or(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
