package com.fitfood.controller;

import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import com.fitfood.product.ProductForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Controller
@RequestMapping("/admin/products")
public class ProductEditController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    public ProductEditController(ProductRepository productRepository, FoodCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy món ăn cần chỉnh sửa!");
            return "redirect:/admin/products";
        }

        ProductForm form = new ProductForm();
        form.setName(product.getName());
        form.setDescription(product.getDescription());
        form.setPrice(product.getPrice());
        form.setActive(product.isActive());

        // Chuyển categoryId từ Long (Product) sang Integer (ProductForm)
        if (product.getCategoryId() != null) {
            form.setCategoryId(product.getCategoryId().intValue());
        }

        model.addAttribute("productId", id);
        model.addAttribute("currentImageUrl", product.getImageUrl());
        model.addAttribute("productForm", form);
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin-product-edit";
    }

    @PostMapping("/edit/{id}")
    public String updateProduct(@PathVariable("id") Long id,
                                @ModelAttribute("productForm") ProductForm form,
                                BindingResult result,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Sản phẩm không tồn tại!");
            return "redirect:/admin/products";
        }

        if (form.getName() == null || form.getName().trim().isEmpty()) {
            result.rejectValue("name", "error.name", "Tên món ăn không được để trống!");
        }
        if (form.getPrice() == null || form.getPrice().doubleValue() < 0) {
            result.rejectValue("price", "error.price", "Giá sản phẩm phải lớn hơn hoặc bằng 0!");
        }
        if (form.getCategoryId() == null) {
            result.rejectValue("categoryId", "error.categoryId", "Vui lòng chọn danh mục!");
        }

        if (result.hasErrors()) {
            model.addAttribute("productId", id);
            model.addAttribute("currentImageUrl", product.getImageUrl());
            model.addAttribute("categories", categoryRepository.findAll());
            return "admin-product-edit";
        }

        // Xử lý upload ảnh mới nếu có chọn
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String originalFilename = imageFile.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String newFileName = UUID.randomUUID() + fileExtension;
                Path uploadPath = Paths.get("uploads/products");
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                try (InputStream inputStream = imageFile.getInputStream()) {
                    Files.copy(inputStream, uploadPath.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
                }
                product.setImageUrl("/uploads/products/" + newFileName);
            } catch (IOException e) {
                model.addAttribute("productId", id);
                model.addAttribute("currentImageUrl", product.getImageUrl());
                model.addAttribute("categories", categoryRepository.findAll());
                model.addAttribute("errorMessage", "Lỗi khi lưu ảnh: " + e.getMessage());
                return "admin-product-edit";
            }
        }

        product.setName(form.getName().trim());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setActive(form.isActive());

        // Chuyển categoryId từ Integer (ProductForm) sang Long (Product)
        if (form.getCategoryId() != null) {
            product.setCategoryId(form.getCategoryId().longValue());
        }

        productRepository.save(product);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật món ăn thành công!");
        return "redirect:/admin/products";
    }
}