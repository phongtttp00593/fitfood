package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
public class ProductDeleteController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    public ProductDeleteController(ProductRepository productRepository, FoodCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/delete/{id}")
    public String showDeleteConfirm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy món ăn cần xóa!");
            return "redirect:/admin/products";
        }

        String categoryName = "Chưa phân loại";
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(FoodCategory::getName)
                    .orElse("Chưa phân loại");
        }

        model.addAttribute("product", product);
        model.addAttribute("categoryName", categoryName);
        return "admin-product-delete";
    }

    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Sản phẩm không tồn tại hoặc đã bị xóa!");
            return "redirect:/admin/products";
        }

        try {
            productRepository.delete(product);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn sản phẩm: " + product.getName());
        } catch (DataIntegrityViolationException ex) {
            // Khi sản phẩm đã có trong đơn hàng (fitfood_order_items) hoặc giỏ hàng (fitfood_cart_items)
            // SQL Server chặn khóa ngoại (FK), tự động chuyển sang ngừng kinh doanh để không gây lỗi DB
            product.setActive(false);
            productRepository.save(product);
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Món ăn đã phát sinh dữ liệu (đơn hàng/giỏ hàng) nên hệ thống đã tự động chuyển sang trạng thái 'Ngưng kinh doanh' để bảo toàn dữ liệu!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm. Lỗi: " + ex.getMessage());
        }

        return "redirect:/admin/products";
    }
}