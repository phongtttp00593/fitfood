package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.meal.CustomerMealPlan;
import com.fitfood.meal.CustomerMealPlanRepository;
import com.fitfood.meal.MealPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AppUserRepository users;
    private final MealPlanRepository mealPlans;
    private final CustomerMealPlanRepository customerMealPlans;
    private final JdbcTemplate jdbc;

    public AccountController(AppUserRepository users, MealPlanRepository mealPlans,
            CustomerMealPlanRepository customerMealPlans, JdbcTemplate jdbc) {
        this.users = users;
        this.mealPlans = mealPlans;
        this.customerMealPlans = customerMealPlans;
        this.jdbc = jdbc;
    }

    @GetMapping("/tai-khoan")
    public String myAccount() { return "account"; }

    @PostMapping("/tai-khoan/goi-an")
    @PreAuthorize("hasRole('USER')")
    public String chooseMealPlan(Authentication authentication,
            @RequestParam Long mealPlanId, RedirectAttributes redirect) {
        AppUser customer = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        var plan = mealPlans.findByIdAndActiveTrue(mealPlanId);
        if (plan.isEmpty()) {
            redirect.addFlashAttribute("mealPlanError", "Gói ăn không còn khả dụng. Bạn hãy chọn gói khác.");
            return "redirect:/menu?category=goi-an#menuApp";
        }
        customerMealPlans.save(new CustomerMealPlan(customer.getId(), plan.get().getId()));
        redirect.addFlashAttribute("mealPlanSuccess", "Bạn đã chọn gói “" + plan.get().getName() + "”.");
        return "redirect:/menu?category=goi-an#menuApp";
    }
    @PostMapping("/tai-khoan/goi-an/mon")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public String changeMealInPlan(Authentication authentication,
            @RequestParam Long mealPlanId,
            @RequestParam Long slotProductId,
            @RequestParam Long productId,
            RedirectAttributes redirect) {
        AppUser customer = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        Integer selectedPlan = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.fitfood_customer_meal_plans
                WHERE user_id = ? AND meal_plan_id = ?
                """, Integer.class, customer.getId(), mealPlanId);
        Integer validSlot = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.fitfood_meal_plan_items
                WHERE meal_plan_id = ? AND product_id = ?
                """, Integer.class, mealPlanId, slotProductId);
        Integer activeProduct = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ? AND active = 1
                """, Integer.class, productId);
        if (selectedPlan == null || selectedPlan == 0 || validSlot == null || validSlot == 0
                || activeProduct == null || activeProduct == 0) {
            redirect.addFlashAttribute("mealPlanError", "Không thể đổi món. Hãy kiểm tra gói ăn và món đã chọn.");
            return "redirect:/menu?category=goi-an#menuApp";
        }

        int updated = jdbc.update("""
                UPDATE dbo.fitfood_customer_meal_plan_items
                SET product_id = ?
                WHERE user_id = ? AND meal_plan_id = ? AND slot_product_id = ?
                """, productId, customer.getId(), mealPlanId, slotProductId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO dbo.fitfood_customer_meal_plan_items
                        (user_id, meal_plan_id, slot_product_id, product_id)
                    VALUES (?, ?, ?, ?)
                    """, customer.getId(), mealPlanId, slotProductId, productId);
        }
        redirect.addFlashAttribute("mealPlanSuccess", "Đã cập nhật món ăn trong gói của bạn.");
        return "redirect:/menu?category=goi-an#menuApp";
    }
}