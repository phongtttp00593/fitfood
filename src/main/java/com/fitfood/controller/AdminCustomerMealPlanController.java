package com.fitfood.controller;

import com.fitfood.auth.service.KhachHangService;
import com.fitfood.meal.CustomerMealPlan;
import com.fitfood.meal.CustomerMealPlanRepository;
import com.fitfood.meal.MealPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/customers/{customerId}/meal-plan")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHACH_HANG')")
public class AdminCustomerMealPlanController {
    private final KhachHangService customers;
    private final MealPlanRepository mealPlans;
    private final CustomerMealPlanRepository customerMealPlans;
    private final JdbcTemplate jdbc;

    public AdminCustomerMealPlanController(KhachHangService customers, MealPlanRepository mealPlans,
            CustomerMealPlanRepository customerMealPlans, JdbcTemplate jdbc) {
        this.customers = customers;
        this.mealPlans = mealPlans;
        this.customerMealPlans = customerMealPlans;
        this.jdbc = jdbc;
    }

    @GetMapping
    public String detail(@PathVariable Long customerId, Model model) {
        var customer = customers.timKhachHang(customerId);
        Long selectedPlanId = customerMealPlans.findById(customerId)
                .map(CustomerMealPlan::getMealPlanId).orElse(null);
        model.addAttribute("customer", customer);
        model.addAttribute("mealPlans", mealPlans.findByActiveTrueOrderByNameAsc());
        model.addAttribute("selectedPlanId", selectedPlanId);
        model.addAttribute("selectedPlan", selectedPlanId == null ? null
                : mealPlans.findById(selectedPlanId).orElse(null));
        List<MealSlotView> slots = selectedPlanId == null ? List.of() : jdbc.query(
                "SELECT mpi.product_id AS slot_product_id, mpi.quantity, " +
                "COALESCE(cmpi.product_id, mpi.product_id) AS selected_product_id, " +
                "base_product.name AS slot_product_name, selected_product.name AS selected_product_name " +
                "FROM dbo.fitfood_meal_plan_items mpi " +
                "JOIN dbo.fitfood_products base_product ON base_product.id = mpi.product_id " +
                "LEFT JOIN dbo.fitfood_customer_meal_plan_items cmpi ON cmpi.user_id = ? " +
                "AND cmpi.meal_plan_id = mpi.meal_plan_id AND cmpi.slot_product_id = mpi.product_id " +
                "JOIN dbo.fitfood_products selected_product ON selected_product.id = COALESCE(cmpi.product_id, mpi.product_id) " +
                "WHERE mpi.meal_plan_id = ? ORDER BY base_product.name",
                (rs, rowNum) -> new MealSlotView(rs.getLong("slot_product_id"), rs.getInt("quantity"),
                        rs.getLong("selected_product_id"), rs.getString("slot_product_name"),
                        rs.getString("selected_product_name")), customerId, selectedPlanId);
        model.addAttribute("mealSlots", slots);
        model.addAttribute("mealDishOptions", jdbc.query(
                "SELECT id, name, price FROM dbo.fitfood_products WHERE active = 1 ORDER BY name",
                (rs, rowNum) -> new MealDishOption(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price"))));
        return "admin-customer-meal-plan";
    }

    @PostMapping
    @Transactional
    public String assign(@PathVariable Long customerId, @RequestParam Long mealPlanId,
            @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        customers.timKhachHang(customerId);
        var plan = mealPlans.findByIdAndActiveTrue(mealPlanId);
        if (plan.isEmpty()) {
            redirect.addFlashAttribute("mealPlanError", "Gói ăn không còn hoạt động. Hãy chọn gói khác.");
            return redirectTo(customerId);
        }
        var existing = customerMealPlans.findById(customerId);
        if (existing.isPresent() && !existing.get().getMealPlanId().equals(mealPlanId)) {
            jdbc.update("DELETE FROM dbo.fitfood_customer_meal_plan_items WHERE user_id = ?", customerId);
        }
        customerMealPlans.save(new CustomerMealPlan(customerId, mealPlanId));
        redirect.addFlashAttribute("success", "Đã cập nhật gói ăn “" + plan.get().getName() + "” cho khách hàng.");
        if (returnTo != null && returnTo.matches("/admin/meal-plans/[0-9]+/customers")) {
            return "redirect:" + returnTo;
        }
        return redirectTo(customerId);
    }

    @PostMapping("/dish")
    @Transactional
    public String updateDish(@PathVariable Long customerId, @RequestParam Long mealPlanId,
            @RequestParam Long slotProductId, @RequestParam Long productId, RedirectAttributes redirect) {
        customers.timKhachHang(customerId);
        Integer selectedPlan = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.fitfood_customer_meal_plans WHERE user_id = ? AND meal_plan_id = ?",
                Integer.class, customerId, mealPlanId);
        Integer validSlot = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ? AND product_id = ?",
                Integer.class, mealPlanId, slotProductId);
        Integer activeProduct = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ? AND active = 1",
                Integer.class, productId);
        if (selectedPlan == null || selectedPlan == 0 || validSlot == null || validSlot == 0
                || activeProduct == null || activeProduct == 0) {
            redirect.addFlashAttribute("mealPlanError", "Không thể cập nhật món. Hãy kiểm tra lại gói và món ăn.");
            return redirectTo(customerId);
        }
        int updated = jdbc.update("UPDATE dbo.fitfood_customer_meal_plan_items SET product_id = ? " +
                "WHERE user_id = ? AND meal_plan_id = ? AND slot_product_id = ?",
                productId, customerId, mealPlanId, slotProductId);
        if (updated == 0) {
            jdbc.update("INSERT INTO dbo.fitfood_customer_meal_plan_items " +
                    "(user_id, meal_plan_id, slot_product_id, product_id) VALUES (?, ?, ?, ?)",
                    customerId, mealPlanId, slotProductId, productId);
        }
        redirect.addFlashAttribute("mealPlanSuccess", "Đã cập nhật món ăn trong gói của khách hàng.");
        return redirectTo(customerId);
    }

    private String redirectTo(Long customerId) {
        return "redirect:/admin/customers/" + customerId + "/meal-plan";
    }

    public record MealSlotView(Long slotProductId, int quantity, Long selectedProductId,
            String slotProductName, String selectedProductName) { }
    public record MealDishOption(Long id, String name, BigDecimal price) { }
}

