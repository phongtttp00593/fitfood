
package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    public AdminController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String admin(Model model) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate month = today.withDayOfMonth(1);
        LocalDate end = today.plusDays(1);
        LocalDate week = today.minusDays(6);
        String paid = """
    o.status = N'Hoàn thành'
    AND EXISTS (
        SELECT 1
        FROM dbo.fitfood_deliveries d
        WHERE d.order_id = o.id
          AND d.status = N'Đã giao'
    )
    AND EXISTS (
        SELECT 1
        FROM dbo.fitfood_payments p
        WHERE p.order_id = o.id
          AND p.status = N'Đã thanh toán'
    )
    """;
        model.addAttribute("monthLabel", today.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        model.addAttribute("revenue", revenue(month, end, paid));
        model.addAttribute("totalOrders", jdbc.queryForObject("SELECT COUNT(*) FROM dbo.fitfood_orders", Integer.class));
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("activeProducts", productRepository.findAll().stream().filter(Product::isActive).count());
        model.addAttribute("totalCustomers", jdbc.queryForObject("SELECT COUNT(*) FROM dbo.fitfood_users WHERE role = 'USER'", Integer.class));
        List<Map<String,Object>> days = new ArrayList<>();
        BigDecimal max = BigDecimal.ZERO, sum = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            LocalDate d = week.plusDays(i);
            BigDecimal value = revenue(d, d.plusDays(1), paid);
            days.add(new java.util.HashMap<>(Map.of("label", d.format(DateTimeFormatter.ofPattern("dd/MM")), "value", value)));
            max = max.max(value); sum = sum.add(value);
        }
        BigDecimal step = max.divide(BigDecimal.valueOf(5), 0, RoundingMode.CEILING).max(BigDecimal.ONE);
        BigDecimal ceiling = step.multiply(BigDecimal.valueOf(5));
        List<BigDecimal> axes = new ArrayList<>();
        for (int i = 5; i >= 0; i--) axes.add(step.multiply(BigDecimal.valueOf(i)));
        for (Map<String,Object> day : days) day.put("height", ((BigDecimal) day.get("value")).multiply(BigDecimal.valueOf(100)).divide(ceiling, 2, RoundingMode.HALF_UP));
        model.addAttribute("days", days);
        model.addAttribute("axes", axes);
        model.addAttribute("weekRevenue", sum);
        List<Map<String,Object>> statuses = jdbc.queryForList("SELECT status, COUNT(*) AS n FROM dbo.fitfood_orders GROUP BY status");
        List<Map<String,Object>> groups = new ArrayList<>();
        String[] names = {"Hoàn thành", "Đang giao", "Đang xử lý", "Chờ xác nhận", "Đã hủy", "Khác"};
        String[] colors = {"#2f6f5a", "#56afe0", "#b8862b", "#dcc07e", "#9b2c1f", "#9ca3af"};
        int[] counts = new int[names.length];
        int total = 0;
        for (Map<String,Object> row : statuses) {
            String status = String.valueOf(row.get("status"));
            int index = java.util.Arrays.asList(names).indexOf(status);
            int n = ((Number) row.get("n")).intValue();
            counts[index < 0 ? 5 : index] += n; total += n;
        }
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        int cumulative = 0;
        for (int i = 0; i < names.length; i++) {
            if (i == 5 && counts[i] == 0) continue;
            groups.add(Map.of("name", names[i], "color", colors[i], "count", counts[i]));
            if (counts[i] == 0 || total == 0) continue;
            if (cumulative > 0) gradient.append(", ");
            gradient.append(colors[i]).append(' ').append(100.0 * cumulative / total).append("% ");
            cumulative += counts[i];
            gradient.append(100.0 * cumulative / total).append('%');
        }
        model.addAttribute("statusGroups", groups);
        model.addAttribute("statusTotal", total);
        model.addAttribute("donutBackground", total == 0 ? "#e5e7eb" : gradient.append(')').toString());
        model.addAttribute("recentOrders", jdbc.queryForList("""
                SELECT TOP 5 o.id AS orderId, COALESCE(u.full_name, N'Khách hàng') AS customerName,
                    CONVERT(varchar(10), o.placed_at, 103) AS orderDate, o.total, o.status
                FROM dbo.fitfood_orders o LEFT JOIN dbo.fitfood_users u ON u.id = o.user_id
                ORDER BY o.placed_at DESC, o.id DESC
                """));
        model.addAttribute("topFoods", jdbc.queryForList("""
                SELECT TOP 3 pr.id, pr.name, pr.image_url AS imageUrl, SUM(oi.quantity) AS sold
                FROM dbo.fitfood_order_items oi JOIN dbo.fitfood_products pr ON pr.id = oi.product_id
                JOIN dbo.fitfood_orders o ON o.id = oi.order_id WHERE
                """ + paid + " GROUP BY pr.id, pr.name, pr.image_url ORDER BY sold DESC, pr.id"));
        model.addAttribute("inventory", jdbc.queryForList("""
                SELECT TOP 3 p.name, i.stock_quantity - i.reserved_quantity AS available, i.reorder_level AS threshold
                FROM dbo.fitfood_inventory i JOIN dbo.fitfood_products p ON p.id = i.product_id
                ORDER BY i.stock_quantity - i.reserved_quantity, p.id
                """));
        return "admin";
    }

    private BigDecimal revenue(LocalDate from, LocalDate to, String paid) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(o.total), 0) FROM dbo.fitfood_orders o WHERE o.placed_at >= ? AND o.placed_at < ? AND " + paid,
                BigDecimal.class, Timestamp.valueOf(from.atStartOfDay()), Timestamp.valueOf(to.atStartOfDay()));
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    @GetMapping("/products")
    public String products(Model model) {

        List<Product> products = productRepository.findAll(
                Sort.by(Sort.Direction.DESC, "id"));

        List<FoodCategory> categories = categoryRepository.findAll();

        Map<Long, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(
                        FoodCategory::getId,
                        FoodCategory::getName,
                        (first, second) -> first));

        long activeCount = products.stream()
                .filter(Product::isActive)
                .count();

        long inactiveCount = products.size() - activeCount;

        model.addAttribute("products", products);
        model.addAttribute("categoryNames", categoryNames);
        model.addAttribute("totalProducts", products.size());
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("categoryCount", categories.size());

        // Trả về giao diện danh sách sản phẩm hiện có.
        return "admin-products";
    }
}
