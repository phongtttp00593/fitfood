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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAddController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;
    private final String uploadDir;

    public ProductAddController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository,
            @Value("${app.upload.dir:uploads/products}") String uploadDir) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uploadDir = uploadDir;
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductForm());
        }
        addCategoriesToModel(model);
        return "admin-product-add";
    }

    @PostMapping("/add")
    public String processAdd(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            addCategoriesToModel(model);
            return "admin-product-add";
        }

        Product product = new Product();
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
            } catch (IOException e) {
                bindingResult.rejectValue("imageFile", "upload.failed", "Không thể lưu tệp ảnh, vui lòng thử lại.");
                addCategoriesToModel(model);
                return "admin-product-add";
            }
        }

        productRepository.save(product);

        // KHÔNG redirect về /admin/products để tránh iframe tự tải lại cả trang admin vào popup
        model.addAttribute("successMessage", "Đã thêm món ăn thành công!");
        addCategoriesToModel(model);
        return "admin-product-add";
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