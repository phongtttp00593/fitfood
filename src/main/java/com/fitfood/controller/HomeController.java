
package com.fitfood.controller;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final JdbcTemplate jdbc;

    public HomeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================= TRANG CHU =================

    @GetMapping("/")
    public String home(Model model) {

        model.addAttribute("featuredProducts", jdbc.query("""
            SELECT TOP (8)
                p.id,
                p.name,
                p.description,
                p.price,
                p.image_url,
                c.name AS category_name
            FROM dbo.fitfood_products p
            LEFT JOIN dbo.fitfood_categories c
                ON c.id = p.category_id
            WHERE p.active = 1
            ORDER BY p.created_at DESC, p.id DESC
            """, (rs, rowNum) -> {

                BigDecimal price = rs.getBigDecimal("price");

                DecimalFormat formatter = new DecimalFormat(
                    "#,##0",
                    DecimalFormatSymbols.getInstance(
                        new Locale("vi", "VN")
                    )
                );

                String imageUrl = rs.getString("image_url");

                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = "/images/hero-bowl.jpg";
                }

                return new FeaturedProduct(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    price,
                    formatter.format(price) + "đ",
                    imageUrl,
                    rs.getString("category_name") == null
                        ? "Món ngon"
                        : rs.getString("category_name")
                );

            }));

        return "index";
    }

    // ================= THUC DON =================

    @GetMapping("/menu")
    public String menu(Model model) {

        // Giữ các thuộc tính rỗng để tương thích
        // với những template cũ chưa được dọn sạch.
        model.addAttribute("mealPlans", List.of());
        model.addAttribute("mealPlanDishes", Map.of());
        model.addAttribute("selectedMealPlanId", null);
        model.addAttribute("selectedMealPlanName", null);
        model.addAttribute("mealSlots", List.of());
        model.addAttribute("mealDishOptions", List.of());

        // Danh sách món ăn
        model.addAttribute("menuProducts", jdbc.query("""
            SELECT id, name, description, price, image_url
            FROM dbo.fitfood_products
            WHERE active = 1
            ORDER BY name
            """, (rs, rowNum) -> new MenuProduct(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("image_url")
            )));

        // Danh sách danh mục
        model.addAttribute("categories", jdbc.queryForList("""
            SELECT c.name
            FROM dbo.fitfood_categories c
            WHERE c.active = 1
            ORDER BY c.name
            """, String.class));

        // Danh sách sản phẩm thực đơn V2
        // Giá thấp nhất của các món đang bán (hiển thị ở đầu trang Thực đơn).
        BigDecimal minPrice = jdbc.queryForObject(
            "SELECT MIN(price) FROM dbo.fitfood_products WHERE active = 1",
            BigDecimal.class);
        model.addAttribute("minPriceLabel", minPrice == null ? null
            : new DecimalFormat("#,##0",
                DecimalFormatSymbols.getInstance(new java.util.Locale("vi", "VN")))
                .format(minPrice) + "đ");

        model.addAttribute("products", jdbc.query("""
            SELECT
                p.id,
                p.name,
                p.description,
                p.price,
                p.image_url,
                c.name AS category_name
            FROM dbo.fitfood_products p
            INNER JOIN dbo.fitfood_categories c
                ON c.id = p.category_id
            WHERE p.active = 1
              AND c.active = 1
            ORDER BY p.name
            """, (rs, rowNum) -> {

                BigDecimal price = rs.getBigDecimal("price");

                DecimalFormatSymbols symbols =
                    DecimalFormatSymbols.getInstance(
                        new Locale("vi", "VN")
                    );

                DecimalFormat formatter =
                    new DecimalFormat("#,##0", symbols);

                return new MenuPageProduct(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    price,
                    formatter.format(price) + "đ",
                    rs.getString("image_url"),
                    rs.getString("category_name")
                );

            }));

        return "menu";
    }

    // ================= CHI TIET MON AN =================

    @GetMapping("/product")
    public String product(
            @RequestParam(value = "id", required = false) Long id,
            Model model) {

        if (id == null || id <= 0) {
            return "redirect:/menu";
        }

        List<FeaturedProduct> matches = jdbc.query("""
            SELECT
                p.id,
                p.name,
                p.description,
                p.price,
                p.image_url,
                c.name AS category_name
            FROM dbo.fitfood_products p
            LEFT JOIN dbo.fitfood_categories c
                ON c.id = p.category_id
            WHERE p.id = ?
              AND p.active = 1
            """, (rs, rowNum) -> {

                BigDecimal price = rs.getBigDecimal("price");

                DecimalFormat formatter = new DecimalFormat(
                    "#,##0",
                    DecimalFormatSymbols.getInstance(
                        new Locale("vi", "VN")
                    )
                );

                String imageUrl = rs.getString("image_url");

                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = "/images/hero-bowl.jpg";
                }

                return new FeaturedProduct(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    price,
                    formatter.format(price) + "đ",
                    imageUrl,
                    rs.getString("category_name") == null
                        ? "Món ngon"
                        : rs.getString("category_name")
                );

            }, id);

        if (matches.isEmpty()) {
            return "redirect:/menu";
        }

        FeaturedProduct product = matches.get(0);

        model.addAttribute("product", product);

        model.addAttribute(
            "variantGroups",
            loadProductVariantGroups(id)
        );

        // Gợi ý tối đa 4 món cùng danh mục ở cuối trang chi tiết.
        model.addAttribute("relatedProducts", jdbc.query("""
            SELECT TOP (4) p.id, p.name, p.price, p.image_url
            FROM dbo.fitfood_products p
            WHERE p.active = 1 AND p.id <> ?
              AND p.category_id = (
                  SELECT category_id FROM dbo.fitfood_products WHERE id = ?)
            ORDER BY p.name
            """, (rs, rowNum) -> new RelatedProduct(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getString("image_url")), id, id));

        return "product";
    }

    // ================= BIEN THE MON AN =================

    private List<Map<String, Object>> loadProductVariantGroups(
            Long productId) {

        List<Map<String, Object>> groups = jdbc.queryForList("""
            SELECT
                id,
                group_name,
                is_required,
                allow_multiple,
                max_select,
                sort_order
            FROM dbo.food_variant_groups
            WHERE food_id = ?
            ORDER BY sort_order, id
            """, productId);

        for (Map<String, Object> group : groups) {

            Object rawId = group.get("id");

            Long groupId = ((Number) rawId).longValue();

            group.put("options", jdbc.queryForList("""
                SELECT
                    id,
                    option_name,
                    extra_price,
                    extra_calories,
                    is_default,
                    is_available
                FROM dbo.food_variant_options
                WHERE group_id = ?
                  AND is_available = 1
                ORDER BY id
                """, groupId));

        }

        return groups;
    }

    // ================= DU LIEU GIAO DIEN =================

    public record MenuPageProduct(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String priceLabel,
            String imageUrl,
            String categoryName) {
    }

    public record MenuProduct(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String imageUrl) {
    }

    public record RelatedProduct(
            Long id,
            String name,
            BigDecimal price,
            String imageUrl) {
    }

    public record FeaturedProduct(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String priceLabel,
            String imageUrl,
            String categoryName) {
    }
}
