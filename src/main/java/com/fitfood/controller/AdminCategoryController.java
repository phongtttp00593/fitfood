package com.fitfood.controller;

import com.fitfood.category.CategoryForm;
import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_DANH_MUC')")
public class AdminCategoryController {

    private final FoodCategoryService service;

    public AdminCategoryController(FoodCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "all") String status,
                       Model model) {
        model.addAttribute("categories", service.search(keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("total", service.countAll());
        model.addAttribute("activeCount", service.countActive());
        model.addAttribute("inactiveCount", service.countInactive());
        return "admin-categories";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        return "admin-category-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id,
                       Model model,
                       RedirectAttributes redirect) {
        try {
            FoodCategory category = service.findById(id);
            CategoryForm form = new CategoryForm();
            form.setId(category.getId());
            form.setName(category.getName());
            form.setDescription(category.getDescription());
            form.setActive(category.isActive());
            model.addAttribute("categoryForm", form);
            return "admin-category-form";
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", "Không tìm thấy danh mục DM" + id + ".");
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("categoryForm") CategoryForm form,
                       BindingResult errors,
                       RedirectAttributes redirect) {
        if (form.getName() != null && !form.getName().isBlank()) {
            String normalizedName = form.getName().strip();
            if (service.nameUsedByAnotherCategory(normalizedName, form.getId())) {
                errors.rejectValue("name", "duplicate", "Tên danh mục đã tồn tại.");
            }
        }

        if (errors.hasErrors()) {
            return "admin-category-form";
        }

        try {
            boolean isNew = (form.getId() == null);
            service.save(form);
            redirect.addFlashAttribute("success", isNew
                    ? "Đã thêm danh mục \"" + form.getName().strip() + "\" thành công."
                    : "Đã cập nhật danh mục DM" + form.getId() + " thành công.");
            return "redirect:/admin/categories";
        } catch (DataIntegrityViolationException ex) {
            errors.rejectValue("name", "duplicate", "Tên danh mục đã tồn tại hoặc dữ liệu không hợp lệ.");
            return "admin-category-form";
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable("id") Long id, RedirectAttributes redirect) {
        try {
            FoodCategory category = service.findById(id);
            service.toggle(id);
            String stateText = !category.isActive() ? "Đang hoạt động" : "Tạm ẩn";
            redirect.addFlashAttribute("success",
                    "Đã chuyển danh mục \"" + category.getName() + "\" (DM" + id + ") sang trạng thái: " + stateText + ".");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", "Không tìm thấy danh mục DM" + id + ".");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Không thể cập nhật trạng thái danh mục DM" + id + ".");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirect) {
        try {
            FoodCategory category = service.findById(id);
            String catName = category.getName();
            service.delete(id);
            redirect.addFlashAttribute("success",
                    "Đã xóa danh mục \"" + catName + "\" (DM" + id + ") thành công.");
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("error",
                    "Không thể xóa danh mục DM" + id + " vì đang có món ăn thuộc danh mục này. Vui lòng chuyển các món ăn sang danh mục khác hoặc chọn 'Tạm ẩn'!");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", "Không tìm thấy danh mục DM" + id + " để xóa.");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error",
                    "Lỗi không thể xóa danh mục DM" + id + ": " + ex.getMessage());
        }
        return "redirect:/admin/categories";
    }
}