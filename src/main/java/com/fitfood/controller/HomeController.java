package com.fitfood.controller;

import com.fitfood.auth.repository.AppUserRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fitfood.meal.CustomerMealPlanRepository;
import com.fitfood.meal.MealPlanRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {
    private final MealPlanRepository mealPlans;
    private final AppUserRepository users;
    private final CustomerMealPlanRepository customerMealPlans;
    private final JdbcTemplate jdbc;

    public HomeController(MealPlanRepository mealPlans, AppUserRepository users,
            CustomerMealPlanRepository customerMealPlans, JdbcTemplate jdbc) {
        this.mealPlans = mealPlans;
        this.users = users;
        this.customerMealPlans = customerMealPlans;
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public String home(Model model) {
        // Homepage v2 expects featuredProducts; without this attribute Thymeleaf renders
        // the "Món ngon hôm nay" grid empty even though products exist in SQL Server.
        model.addAttribute("featuredProducts", jdbc.query("""
            SELECT TOP (8) p.id, p.name, p.description, p.price, p.image_url,
                   c.name AS category_name
            FROM dbo.fitfood_products p
            LEFT JOIN dbo.fitfood_categories c ON c.id = p.category_id
            WHERE p.active = 1
            ORDER BY p.created_at DESC, p.id DESC
            """, (rs, rowNum) -> {
                BigDecimal price = rs.getBigDecimal("price");
                DecimalFormat formatter = new DecimalFormat("#,##0",
                    DecimalFormatSymbols.getInstance(new Locale("vi", "VN")));
                String imageUrl = rs.getString("image_url");
                if (imageUrl == null || imageUrl.isBlank()) imageUrl = "/images/hero-bowl.jpg";
                return new FeaturedProduct(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), price, formatter.format(price) + "đ",
                    imageUrl, rs.getString("category_name") == null ? "Món ngon" : rs.getString("category_name"));
            }));
        return "index";
    }

    @GetMapping("/meal-plan/{id}")
    public String mealPlanDetail(@org.springframework.web.bind.annotation.PathVariable("id") Long id, Model model) {
        var plans = jdbc.query("SELECT id, name, description, price, number_of_days FROM dbo.fitfood_meal_plans WHERE id = ? AND active = 1",
            (rs, rowNum) -> new MealPlanDetailView(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getObject("number_of_days", Integer.class)), id);
        if (plans.isEmpty()) return "redirect:/menu#goi-an";
        model.addAttribute("mealPlan", plans.get(0));
        model.addAttribute("dishes", jdbc.query("""
            SELECT p.id, p.name, p.description, p.image_url, p.price, mpi.quantity
            FROM dbo.fitfood_meal_plan_items mpi
            JOIN dbo.fitfood_products p ON p.id = mpi.product_id
            WHERE mpi.meal_plan_id = ? AND p.active = 1
            ORDER BY p.name
            """, (rs, rowNum) -> new MealPlanDetailDish(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"), rs.getBigDecimal("price"), rs.getInt("quantity")), id));
        return "v2/meal-plan-detail";
    }

    @GetMapping("/menu")
    public String menu(Model model, Authentication authentication) {
        model.addAttribute("mealPlans", mealPlans.findByActiveTrueOrderByNameAsc());
        // Món trong từng gói do Admin cấu hình; USER chỉ xem chi tiết và mua gói, không tự đổi món.
        Map<Long, List<MealPlanDishView>> mealPlanDishes = new java.util.LinkedHashMap<>();
        for (var plan : mealPlans.findByActiveTrueOrderByNameAsc()) {
            List<MealPlanDishView> dishes = jdbc.query("""
                SELECT p.name, p.description, p.image_url, mpi.quantity
                FROM dbo.fitfood_meal_plan_items mpi
                JOIN dbo.fitfood_products p ON p.id = mpi.product_id
                WHERE mpi.meal_plan_id = ? AND p.active = 1
                ORDER BY p.name
                """, (rs, rowNum) -> new MealPlanDishView(rs.getString("name"),
                    rs.getString("description"), rs.getString("image_url"), rs.getInt("quantity")), plan.getId());
            mealPlanDishes.put(plan.getId(), dishes);
        }
        model.addAttribute("mealPlanDishes", mealPlanDishes);
        // Lấy món ăn đang bán từ SQL Server để thực đơn luôn đồng bộ với quản trị sản phẩm.
        model.addAttribute("menuProducts", jdbc.query("""
            SELECT id, name, description, price, image_url
            FROM dbo.fitfood_products
            WHERE active = 1
            ORDER BY name
            """, (rs, rowNum) -> new MenuProduct(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getString("image_url"))));

        // Dữ liệu cho giao diện thực đơn v2: danh mục và món ăn đều lấy trực tiếp từ SQL Server.
        model.addAttribute("categories", jdbc.queryForList("""
            SELECT c.name
            FROM dbo.fitfood_categories c
            WHERE c.active = 1
            ORDER BY c.name
            """, String.class));
        model.addAttribute("products", jdbc.query("""
            SELECT p.id, p.name, p.description, p.price, p.image_url, c.name AS category_name
            FROM dbo.fitfood_products p
            INNER JOIN dbo.fitfood_categories c ON c.id = p.category_id
            WHERE p.active = 1 AND c.active = 1
            ORDER BY p.name
            """, (rs, rowNum) -> {
                BigDecimal price = rs.getBigDecimal("price");
                DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("vi", "VN"));
                DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
                return new MenuPageProduct(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), price, formatter.format(price) + "đ",
                    rs.getString("image_url"), rs.getString("category_name"));
            }));
        Long selectedMealPlanId = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            selectedMealPlanId = users.findByEmailIgnoreCase(authentication.getName())
                    .flatMap(user -> customerMealPlans.findById(user.getId()))
                    .map(plan -> plan.getMealPlanId())
                    .orElse(null);
        }
        model.addAttribute("selectedMealPlanId", selectedMealPlanId);
        model.addAttribute("selectedMealPlanName", selectedMealPlanId == null ? null
                : mealPlans.findById(selectedMealPlanId).map(plan -> plan.getName()).orElse(null));
        List<MealSlotView> mealSlots = List.of();
        if (selectedMealPlanId != null && authentication != null) {
            Long userId = users.findByEmailIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
            if (userId != null) {
                mealSlots = jdbc.query("""
                    SELECT mpi.product_id AS slot_product_id, mpi.quantity,
                           COALESCE(cmpi.product_id, mpi.product_id) AS selected_product_id,
                           base_product.name AS slot_product_name,
                           selected_product.name AS selected_product_name
                    FROM dbo.fitfood_meal_plan_items AS mpi
                    JOIN dbo.fitfood_products AS base_product ON base_product.id = mpi.product_id
                    LEFT JOIN dbo.fitfood_customer_meal_plan_items AS cmpi
                      ON cmpi.user_id = ? AND cmpi.meal_plan_id = mpi.meal_plan_id
                     AND cmpi.slot_product_id = mpi.product_id
                    JOIN dbo.fitfood_products AS selected_product
                      ON selected_product.id = COALESCE(cmpi.product_id, mpi.product_id)
                    WHERE mpi.meal_plan_id = ?
                    ORDER BY base_product.name
                    """, (rs, rowNum) -> new MealSlotView(
                        rs.getLong("slot_product_id"), rs.getInt("quantity"),
                        rs.getLong("selected_product_id"), rs.getString("slot_product_name"),
                        rs.getString("selected_product_name")), userId, selectedMealPlanId);
            }
        }
        model.addAttribute("mealSlots", mealSlots);
        model.addAttribute("mealDishOptions", jdbc.query("""
            SELECT id, name, price FROM dbo.fitfood_products WHERE active = 1 ORDER BY name
            """, (rs, rowNum) -> new MealDishOption(rs.getLong("id"),
                rs.getString("name"), rs.getBigDecimal("price"))));
        return "v2/menu";
    }

    public record MenuPageProduct(Long id, String name, String description,
            BigDecimal price, String priceLabel, String imageUrl, String categoryName) { }
    public record MealPlanDishView(String name, String description, String imageUrl, int quantity) { }
    public record MealPlanDetailView(Long id, String name, String description, BigDecimal price, Integer numberOfDays) { }
    public record MealPlanDetailDish(Long id, String name, String description, String imageUrl, BigDecimal price, int quantity) { }

    public record MenuProduct(Long id, String name, String description,
            BigDecimal price, String imageUrl) { }

    public record MealSlotView(Long slotProductId, int quantity, Long selectedProductId,
            String slotProductName, String selectedProductName) { }
    public record MealDishOption(Long id, String name, BigDecimal price) { }

    @GetMapping("/product")
    public String product(@RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null || id <= 0) return "redirect:/menu";
        List<FeaturedProduct> matches = jdbc.query("""
            SELECT p.id, p.name, p.description, p.price, p.image_url,
                   c.name AS category_name
            FROM dbo.fitfood_products p
            LEFT JOIN dbo.fitfood_categories c ON c.id = p.category_id
            WHERE p.id = ? AND p.active = 1
            """, (rs, rowNum) -> {
                BigDecimal price = rs.getBigDecimal("price");
                DecimalFormat formatter = new DecimalFormat("#,##0",
                    DecimalFormatSymbols.getInstance(new Locale("vi", "VN")));
                String imageUrl = rs.getString("image_url");
                if (imageUrl == null || imageUrl.isBlank()) imageUrl = "/images/hero-bowl.jpg";
                return new FeaturedProduct(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), price, formatter.format(price) + "đ",
                    imageUrl, rs.getString("category_name") == null ? "Món ngon" : rs.getString("category_name"));
            }, id);
        if (matches.isEmpty()) return "redirect:/menu";
        FeaturedProduct product = matches.get(0);
        model.addAttribute("product", product);
        model.addAttribute("variantGroups", loadProductVariantGroups(id));
        return "product";
    }

    private List<Map<String, Object>> loadProductVariantGroups(Long productId) {
        List<Map<String, Object>> groups = jdbc.queryForList("""
            SELECT id, group_name, is_required, allow_multiple, max_select, sort_order
            FROM dbo.food_variant_groups WHERE food_id = ? ORDER BY sort_order, id
            """, productId);
        for (Map<String, Object> group : groups) {
            Object rawId = group.get("id");
            Long groupId = ((Number) rawId).longValue();
            group.put("options", jdbc.queryForList("""
                SELECT id, option_name, extra_price, extra_calories, is_default, is_available
                FROM dbo.food_variant_options
                WHERE group_id = ? AND is_available = 1
                ORDER BY id
                """, groupId));
        }
        return groups;
    }

    public record FeaturedProduct(Long id, String name, String description,
            BigDecimal price, String priceLabel, String imageUrl, String categoryName) { }

    // Đăng nhập /dang-nhap được xử lý ở AuthController, KHÔNG khai báo ở đây.
}
