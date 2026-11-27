
package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductDeleteController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    public ProductDeleteController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/delete/{id}")
    public String showDeleteConfirm(
            @PathVariable("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Không tìm thấy sản phẩm cần xóa!"
            );
            return "redirect:/admin/products";
        }

        String categoryName = "Chưa phân loại";

        if (product.getCategoryId() != null) {
            categoryName = categoryRepository
                    .findById(product.getCategoryId())
                    .map(FoodCategory::getName)
                    .orElse("Chưa phân loại");
        }

        model.addAttribute("product", product);
        model.addAttribute("categoryName", categoryName);

        return "admin-product-delete";
    }

    @PostMapping("/delete/{id}")
    public String deleteProduct(
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes) {

        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Sản phẩm không tồn tại hoặc đã bị xóa!"
            );
            return "redirect:/admin/products";
        }

        try {
            productRepository.delete(product);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã xóa sản phẩm: " + product.getName()
            );

        } catch (DataIntegrityViolationException ex) {

            // Bảo toàn dữ liệu đơn hàng và giỏ hàng.
            product.setActive(false);
            productRepository.save(product);

            redirectAttributes.addFlashAttribute(
                    "warningMessage",
                    "Sản phẩm đã phát sinh dữ liệu đơn hàng/giỏ hàng "
                    + "nên được chuyển sang Ngừng bán để bảo toàn dữ liệu!"
            );

        } catch (Exception ex) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Không thể xóa sản phẩm. Vui lòng thử lại "
                    + "hoặc kiểm tra dữ liệu liên quan."
            );
        }

        return "redirect:/admin/products";
    }
}
