package com.fitfood.controller;

import com.fitfood.auth.repository.AppUserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fitfood.meal.CustomerMealPlanRepository;
import com.fitfood.meal.MealPlanRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

    @GetMapping("/") public String home() { return "index"; }

    @GetMapping("/menu")
    public String menu(Model model, Authentication authentication) {
        model.addAttribute("mealPlans", mealPlans.findByActiveTrueOrderByNameAsc());
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
        return "menu";
    }

    public record MealSlotView(Long slotProductId, int quantity, Long selectedProductId,
            String slotProductName, String selectedProductName) { }
    public record MealDishOption(Long id, String name, BigDecimal price) { }

    @GetMapping("/product") public String product() { return "product"; }
    @GetMapping("/cart") public String cart() { return "cart"; }
    // Đăng nhập /dang-nhap được xử lý ở AuthController, KHÔNG khai báo ở đây.
}
