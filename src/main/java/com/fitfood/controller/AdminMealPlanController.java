package com.fitfood.controller;

import com.fitfood.auth.service.KhachHangService;
import com.fitfood.meal.MealPlan;
import com.fitfood.meal.MealPlanCreateForm;
import com.fitfood.meal.MealPlanRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/meal-plans")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHACH_HANG')")
public class AdminMealPlanController {
    private final JdbcTemplate jdbc;
    private final MealPlanRepository mealPlans;
    private final KhachHangService customers;

    public AdminMealPlanController(JdbcTemplate jdbc, MealPlanRepository mealPlans, KhachHangService customers) {
        this.jdbc = jdbc;
        this.mealPlans = mealPlans;
        this.customers = customers;
    }

    @GetMapping
    public String list(Model model) {
        List<MealPlanSummary> plans = jdbc.query(
                "SELECT mp.id, mp.name, mp.description, mp.price, mp.number_of_days, mp.available_from, mp.active, " +
                "COUNT(mpi.product_id) AS product_count " +
                "FROM dbo.fitfood_meal_plans mp " +
                "LEFT JOIN dbo.fitfood_meal_plan_items mpi ON mpi.meal_plan_id = mp.id " +
                "GROUP BY mp.id, mp.name, mp.description, mp.price, mp.number_of_days, mp.available_from, mp.active " +
                "ORDER BY mp.number_of_days, mp.name",
                (rs, rowNum) -> new MealPlanSummary(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"),
                        rs.getObject("number_of_days", Integer.class), rs.getObject("available_from", LocalDate.class),
                        rs.getBoolean("active"), rs.getInt("product_count")));
        model.addAttribute("mealPlans", plans);
        model.addAttribute("activeCount", plans.stream().filter(MealPlanSummary::active).count());
        model.addAttribute("productCount", plans.stream().mapToInt(MealPlanSummary::productCount).sum());
        return "admin-meal-plans";
    }

    @GetMapping("/{mealPlanId}/customers")
    public String assignCustomers(@PathVariable Long mealPlanId, Model model) {
        MealPlanView plan = findMealPlan(mealPlanId);
        Set<Long> assignedIds = new HashSet<>(jdbc.queryForList(
                "SELECT user_id FROM dbo.fitfood_customer_meal_plans WHERE meal_plan_id = ?",
                Long.class, mealPlanId));
        List<CustomerAssignmentView> customerOptions = customers.timKiemKhachHang("", "active").stream()
                .map(customer -> new CustomerAssignmentView(customer.getAccount().getId(),
                        customer.getAccount().getFullName(), customer.getAccount().getEmail(),
                        assignedIds.contains(customer.getAccount().getId())))
                .toList();
        model.addAttribute("mealPlan", plan);
        model.addAttribute("customerOptions", customerOptions);
        return "admin-meal-plan-customers";
    }
    @GetMapping("/{mealPlanId}/edit")
    public String editForm(@PathVariable Long mealPlanId, Model model) {
        MealPlanView plan = findMealPlan(mealPlanId);
        MealPlanCreateForm form = new MealPlanCreateForm();
        form.setName(plan.name()); form.setDescription(plan.description()); form.setPrice(plan.price());
        form.setNumberOfDays(plan.numberOfDays()); form.setAvailableFrom(plan.availableFrom());
        form.setProductIds(jdbc.queryForList("SELECT product_id FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ?", Long.class, mealPlanId));
        model.addAttribute("mealPlanForm", form); addProductOptions(model);
        model.addAttribute("formAction", "/admin/meal-plans/" + mealPlanId + "/edit");
        model.addAttribute("pageTitle", "Sửa gói ăn"); model.addAttribute("submitLabel", "Lưu thay đổi");
        return "admin-meal-plan-form";
    }

    @PostMapping("/{mealPlanId}/edit")
    @Transactional
    public String update(@PathVariable Long mealPlanId, @Valid @ModelAttribute("mealPlanForm") MealPlanCreateForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        findMealPlan(mealPlanId);
        Set<Long> selected = form.getProductIds() == null ? Set.of() : new LinkedHashSet<>(form.getProductIds());
        if (selected.isEmpty()) errors.rejectValue("productIds", "empty", "Hãy chọn ít nhất một sản phẩm cho gói.");
        for (Long productId : selected) {
            Integer activeProduct = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ? AND active = 1", Integer.class, productId);
            if (activeProduct == null || activeProduct == 0) { errors.rejectValue("productIds", "inactive", "Có sản phẩm đã ngừng bán. Hãy chọn món đang bán."); break; }
        }
        if (errors.hasErrors()) {
            addProductOptions(model); model.addAttribute("formAction", "/admin/meal-plans/" + mealPlanId + "/edit");
            model.addAttribute("pageTitle", "Sửa gói ăn"); model.addAttribute("submitLabel", "Lưu thay đổi"); return "admin-meal-plan-form";
        }
        jdbc.update("UPDATE dbo.fitfood_meal_plans SET [name] = ?, [description] = ?, price = ?, number_of_days = ?, available_from = ? WHERE id = ?",
                form.getName().strip(), form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().strip(),
                form.getPrice(), form.getNumberOfDays(), form.getAvailableFrom(), mealPlanId);
        syncProducts(mealPlanId, selected); redirect.addFlashAttribute("success", "Đã lưu thay đổi gói ăn.");
        return "redirect:/admin/meal-plans";
    }

    @PostMapping("/{mealPlanId}/delete")
    @Transactional
    public String delete(@PathVariable Long mealPlanId, RedirectAttributes redirect) {
        MealPlanView plan = findMealPlan(mealPlanId);
        Integer assigned = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.fitfood_customer_meal_plans WHERE meal_plan_id = ?", Integer.class, mealPlanId);
        if (assigned != null && assigned > 0) {
            redirect.addFlashAttribute("error", "Không thể xóa gói “" + plan.name() + "” vì vẫn đang được khách hàng sử dụng. Hãy cập nhật khách sang gói khác trước.");
            return "redirect:/admin/meal-plans";
        }
        jdbc.update("DELETE FROM dbo.fitfood_customer_meal_plan_items WHERE meal_plan_id = ?", mealPlanId);
        jdbc.update("DELETE FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ?", mealPlanId);
        mealPlans.deleteById(mealPlanId); redirect.addFlashAttribute("success", "Đã xóa gói ăn “" + plan.name() + "”.");
        return "redirect:/admin/meal-plans";
    }
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("mealPlanForm", new MealPlanCreateForm());
        model.addAttribute("formAction", "/admin/meal-plans");
        model.addAttribute("pageTitle", "Thêm gói ăn mới");
        model.addAttribute("submitLabel", "Thêm gói ăn");
        addProductOptions(model);
        return "admin-meal-plan-form";
    }

    @PostMapping
    @Transactional
    public String create(@Valid @ModelAttribute("mealPlanForm") MealPlanCreateForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        Set<Long> selected = form.getProductIds() == null ? Set.of() : new LinkedHashSet<>(form.getProductIds());
        if (selected.isEmpty()) {
            errors.rejectValue("productIds", "empty", "Hãy chọn ít nhất một sản phẩm cho gói.");
        }
        for (Long productId : selected) {
            Integer activeProduct = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ? AND active = 1",
                    Integer.class, productId);
            if (activeProduct == null || activeProduct == 0) {
                errors.rejectValue("productIds", "inactive", "Có sản phẩm đã ngừng bán. Hãy chọn món đang bán.");
                break;
            }
        }
        if (errors.hasErrors()) {
            addProductOptions(model);
            return "admin-meal-plan-form";
        }

        MealPlan saved = mealPlans.save(new MealPlan(form.getName().strip(),
                form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().strip(),
                form.getPrice(), form.getNumberOfDays(), form.getAvailableFrom(), true));
        for (Long productId : selected) {
            jdbc.update("INSERT INTO dbo.fitfood_meal_plan_items (meal_plan_id, product_id, quantity) VALUES (?, ?, 1)",
                    saved.getId(), productId);
        }
        redirect.addFlashAttribute("success", "Đã thêm gói ăn. Gói đã xuất hiện trên thực đơn user.");
        return "redirect:/admin/meal-plans";
    }

    @GetMapping("/{mealPlanId}")
    public String editProducts(@PathVariable Long mealPlanId, Model model) {
        MealPlanView plan = findMealPlan(mealPlanId);
        List<MealProductOption> products = jdbc.query(
                "SELECT p.id, p.name, p.description, p.price, p.active, " +
                "CASE WHEN mpi.product_id IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS selected " +
                "FROM dbo.fitfood_products p " +
                "LEFT JOIN dbo.fitfood_meal_plan_items mpi ON mpi.meal_plan_id = ? AND mpi.product_id = p.id " +
                "WHERE p.active = 1 OR mpi.product_id IS NOT NULL " +
                "ORDER BY selected DESC, p.name",
                (rs, rowNum) -> new MealProductOption(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"),
                        rs.getBoolean("active"), rs.getBoolean("selected")), mealPlanId);
        model.addAttribute("mealPlan", plan);
        model.addAttribute("products", products);
        model.addAttribute("selectedCount", products.stream().filter(p -> p.selected() && p.active()).count());
        return "admin-meal-plan-products";
    }

    @PostMapping("/{mealPlanId}/products")
    @Transactional
    public String saveProducts(@PathVariable Long mealPlanId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) List<Long> productIds,
            RedirectAttributes redirect) {
        findMealPlan(mealPlanId);
        Set<Long> selected = productIds == null ? Set.of() : new LinkedHashSet<>(productIds);
        if (selected.isEmpty()) {
            redirect.addFlashAttribute("mealPlanError", "Hãy chọn ít nhất một sản phẩm đang bán cho gói ăn.");
            return redirectTo(mealPlanId);
        }
        for (Long productId : selected) {
            Integer activeProduct = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ? AND active = 1",
                    Integer.class, productId);
            if (activeProduct == null || activeProduct == 0) {
                redirect.addFlashAttribute("mealPlanError", "Danh sách có sản phẩm đã ngừng bán. Hãy tải lại và chọn món đang bán.");
                return redirectTo(mealPlanId);
            }
        }

        Set<Long> existing = new HashSet<>(jdbc.queryForList(
                "SELECT product_id FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ?",
                Long.class, mealPlanId));
        for (Long removedProductId : existing) {
            if (!selected.contains(removedProductId)) {
                jdbc.update("DELETE FROM dbo.fitfood_customer_meal_plan_items " +
                        "WHERE meal_plan_id = ? AND slot_product_id = ?", mealPlanId, removedProductId);
                jdbc.update("DELETE FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ? AND product_id = ?",
                        mealPlanId, removedProductId);
            }
        }
        for (Long addedProductId : selected) {
            if (!existing.contains(addedProductId)) {
                jdbc.update("INSERT INTO dbo.fitfood_meal_plan_items (meal_plan_id, product_id, quantity) " +
                        "VALUES (?, ?, 1)", mealPlanId, addedProductId);
            }
        }
        redirect.addFlashAttribute("mealPlanSuccess", "Đã cập nhật sản phẩm trong gói ăn.");
        return redirectTo(mealPlanId);
    }

    private void addProductOptions(Model model) {
        model.addAttribute("productOptions", jdbc.query(
                "SELECT id, name, description, price FROM dbo.fitfood_products WHERE active = 1 ORDER BY name",
                (rs, rowNum) -> new MealProductOption(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"), true, false)));
    }

    private void syncProducts(Long mealPlanId, Set<Long> selected) {
        Set<Long> existing = new HashSet<>(jdbc.queryForList("SELECT product_id FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ?", Long.class, mealPlanId));
        for (Long removedId : existing) if (!selected.contains(removedId)) {
            jdbc.update("DELETE FROM dbo.fitfood_customer_meal_plan_items WHERE meal_plan_id = ? AND slot_product_id = ?", mealPlanId, removedId);
            jdbc.update("DELETE FROM dbo.fitfood_meal_plan_items WHERE meal_plan_id = ? AND product_id = ?", mealPlanId, removedId);
        }
        for (Long addedId : selected) if (!existing.contains(addedId)) jdbc.update("INSERT INTO dbo.fitfood_meal_plan_items (meal_plan_id, product_id, quantity) VALUES (?, ?, 1)", mealPlanId, addedId);
    }
    private MealPlanView findMealPlan(Long mealPlanId) {
        return jdbc.query("SELECT id, name, description, price, number_of_days, available_from, active " +
                "FROM dbo.fitfood_meal_plans WHERE id = ?",
                (rs, rowNum) -> new MealPlanView(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"),
                        rs.getObject("number_of_days", Integer.class), rs.getObject("available_from", LocalDate.class),
                        rs.getBoolean("active")), mealPlanId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String redirectTo(Long mealPlanId) {
        return "redirect:/admin/meal-plans/" + mealPlanId;
    }

    public record CustomerAssignmentView(Long id, String name, String email, boolean alreadyAssigned) { }
    public record MealPlanSummary(Long id, String name, String description, BigDecimal price,
            Integer numberOfDays, LocalDate availableFrom, boolean active, int productCount) { }
    public record MealPlanView(Long id, String name, String description, BigDecimal price,
            Integer numberOfDays, LocalDate availableFrom, boolean active) { }
    public record MealProductOption(Long id, String name, String description, BigDecimal price,
            boolean active, boolean selected) { }
}









