package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
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
    @ResponseBody
    public Object deleteProduct(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            RedirectAttributes redirectAttributes) {

        Product product = productRepository.findById(id).orElse(null);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);

        if (product == null) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Sản phẩm không tồn tại hoặc đã bị xóa!"
                ));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Sản phẩm không tồn tại hoặc đã bị xóa!");
            return "redirect:/admin/products";
        }

        try {
            productRepository.delete(product);

            if (isAjax) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", "success",
                    "message", "Đã xóa sản phẩm khỏi danh sách"
                ));
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn sản phẩm: " + product.getName());

        } catch (DataIntegrityViolationException ex) {
            // Khi sản phẩm đã phát sinh ràng buộc khóa ngoại (đơn hàng/giỏ hàng), chuyển sang ngừng kinh doanh
            product.setActive(false);
            productRepository.save(product);

            if (isAjax) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", "warning",
                    "message", "Món ăn đã có đơn hàng/giỏ hàng, đã chuyển sang 'Ngừng bán'!"
                ));
            }
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Món ăn đã phát sinh dữ liệu (đơn hàng/giỏ hàng) nên hệ thống đã tự động chuyển sang trạng thái 'Ngưng kinh doanh' để bảo toàn dữ liệu!");

        } catch (Exception ex) {
            if (isAjax) {
                return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Không thể xóa sản phẩm. Lỗi: " + ex.getMessage()
                ));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm. Lỗi: " + ex.getMessage());
        }

        return "redirect:/admin/products";
    }
}