
package com.fitfood.controller;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {

    private final JdbcTemplate jdbcTemplate;

    public HomeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private List<Map<String, Object>> loadProducts() {
        String sql = """
            SELECT p.id, p.name, p.description, p.price, p.image_url,
                   c.name AS category_name
            FROM dbo.fitfood_products p
            JOIN dbo.fitfood_categories c ON c.id = p.category_id
            WHERE p.active = 1 AND c.active = 1
            ORDER BY p.id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapProduct(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("image_url"),
                rs.getString("category_name")
        ));
    }

    private Map<String, Object> mapProduct(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String image,
            String category) {

        Map<String, Object> p = new LinkedHashMap<>();

        p.put("id", id);
        p.put("name", name);
        p.put("description",
                description == null ? "" : description);
        p.put("price", price);

        p.put("priceLabel",
                NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                        .format(price) + "đ");

        p.put("imageUrl",
                image == null || image.isBlank()
                        ? "/images/hero-bowl.jpg"
                        : image);

        p.put("categoryName", category);

        return p;
    }

    private List<String> loadCategories() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM dbo.fitfood_categories "
                + "WHERE active = 1 ORDER BY id",
                String.class
        );
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Map<String, Object>> all = loadProducts();

        model.addAttribute(
                "featuredProducts",
                all.subList(0, Math.min(4, all.size()))
        );

        return "index";
    }

    @GetMapping("/menu")
    public String menu(
            @RequestParam(required = false) String category,
            Model model) {

        List<Map<String, Object>> products = loadProducts();

        if (category != null && !category.isBlank()) {
            products = products.stream()
                    .filter(p -> category.equals(p.get("categoryName")))
                    .toList();
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", loadCategories());
        model.addAttribute(
                "selectedCategory",
                category == null ? "" : category
        );

        return "menu";
    }

    @GetMapping("/product")
    public String product(
            @RequestParam(required = false) Long id,
            Model model) {

        if (id == null) {
            return "redirect:/menu";
        }

        List<Map<String, Object>> matches = loadProducts().stream()
                .filter(p -> id.equals(p.get("id")))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy món ăn"
            );
        }

        model.addAttribute("product", matches.get(0));
        model.addAttribute("variantGroups", loadVariantGroups(id));

        return "product";
    }

    private List<Map<String, Object>> loadVariantGroups(Long productId) {
        try {
            List<Map<String, Object>> groups =
                    jdbcTemplate.queryForList("""
                        SELECT id, group_name, is_required,
                               allow_multiple, max_select
                        FROM dbo.food_variant_groups
                        WHERE food_id = ?
                        ORDER BY sort_order, id
                        """, productId);

            for (Map<String, Object> group : groups) {
                Long groupId =
                        ((Number) group.get("id")).longValue();

                group.put("options", jdbcTemplate.queryForList("""
                    SELECT id, option_name, extra_price,
                           extra_calories, is_default, is_available
                    FROM dbo.food_variant_options
                    WHERE group_id = ?
                    ORDER BY id
                    """, groupId));
            }

            return groups;

        } catch (org.springframework.dao.DataAccessException ex) {
            return new ArrayList<>();
        }
    }

    // Đã bỏ phương thức cart() để tránh trùng đường dẫn GET /cart.
    // CartController sẽ đảm nhiệm việc hiển thị giỏ hàng.

    @GetMapping("/admin/variants")
    public String adminVariantsPage() {
        return "admin-variants";
    }
}
