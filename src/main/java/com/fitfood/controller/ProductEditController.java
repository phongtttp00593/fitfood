package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductForm;
import com.fitfood.category.ProductRepository;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductEditController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;
    private final String uploadDir;

    public ProductEditController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository,
            @Value("${app.upload.dir:uploads/products}") String uploadDir) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uploadDir = uploadDir;
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            model.addAttribute("errorMessage", "Không tìm thấy món ăn!");
            return "admin-product-edit";
        }

        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setCategoryId(product.getCategoryId());
        form.setName(product.getName());
        form.setDescription(product.getDescription());
        form.setPrice(product.getPrice());
        form.setStock(product.getStock());
        form.setImageUrl(product.getImageUrl());
        form.setActive(product.isActive());

        model.addAttribute("productForm", form);
        addCategoriesToModel(model);
        return "admin-product-edit";
    }

    @PostMapping("/edit/{id}")
    public String processEdit(
            @PathVariable Long id,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Model model) {

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            model.addAttribute("errorMessage", "Không tìm thấy món ăn!");
            return "admin-product-edit";
        }

        if (bindingResult.hasErrors()) {
            form.setImageUrl(product.getImageUrl());
            addCategoriesToModel(model);
            return "admin-product-edit";
        }

        product.setName(form.getName().trim());
        product.setCategoryId(form.getCategoryId());
        product.setDescription(form.getDescription() != null ? form.getDescription().trim() : null);
        product.setPrice(form.getPrice());
        product.setStock(form.getStock() != null ? form.getStock() : 0);
        product.setActive(form.isActive());

        MultipartFile file = form.getImageFile();
        if (file != null && !file.isEmpty()) {
            try {
                String filename = saveImage(file);
                product.setImageUrl("/uploads/products/" + filename);
                form.setImageUrl(product.getImageUrl());
            } catch (IOException e) {
                bindingResult.rejectValue("imageFile", "upload.failed", "Không thể lưu tệp ảnh, vui lòng thử lại.");
                form.setImageUrl(product.getImageUrl());
                addCategoriesToModel(model);
                return "admin-product-edit";
            }
        }

        productRepository.save(product);

        // Giữ nguyên trang form để JS gửi message đóng popup về trang cha, KHÔNG redirect
        model.addAttribute("successMessage", "Đã cập nhật món ăn thành công!");
        addCategoriesToModel(model);
        return "admin-product-edit";
    }

    private void addCategoriesToModel(Model model) {
        List<FoodCategory> categories = categoryRepository.findAll();
        model.addAttribute("categories", categories);
    }

    private String saveImage(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }
}