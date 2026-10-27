package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
public class ProductAddController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    private final Path uploadDir = Path.of(
            System.getProperty("user.dir"),
            "uploads",
            "products"
    ).toAbsolutePath().normalize();

    public ProductAddController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // Hiển thị form thêm sản phẩm
    @GetMapping("/add")
    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    public String showAddForm(Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new Product());
        }

        loadCategories(model);
        return "admin-product-add";
    }

    // Lưu sản phẩm mới
    @PostMapping("/save")
    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    public String saveProduct(
            @ModelAttribute("productForm") Product product,
            @RequestParam(value = "imageFile", required = false)
            MultipartFile imageFile,
            Model model,
            RedirectAttributes redirectAttributes) {

        // 1. Kiểm tra tên sản phẩm
        if (product.getName() == null || product.getName().isBlank()) {
            return showError(model, product,
                    "Vui lòng nhập tên sản phẩm.");
        }

        // Xóa khoảng trắng đầu/cuối và gộp nhiều khoảng trắng liên tiếp
        String name = product.getName().trim().replaceAll("\\s+", " ");

        if (name.length() > 150) {
            return showError(model, product,
                    "Tên sản phẩm không được vượt quá 150 ký tự.");
        }

        // 2. Kiểm tra tên trùng, không phân biệt chữ hoa/thường
        if (productRepository.existsByNameIgnoreCase(name)) {
            return showError(model, product,
                    "Sản phẩm \"" + name
                    + "\" đã tồn tại. Vui lòng nhập tên khác.");
        }

        // 3. Kiểm tra danh mục
        if (product.getCategoryId() == null
                || !categoryRepository.existsById(product.getCategoryId())) {
            return showError(model, product,
                    "Vui lòng chọn danh mục hợp lệ.");
        }

        FoodCategory category = categoryRepository
                .findById(product.getCategoryId())
                .orElse(null);

        if (category == null || !category.isActive()) {
            return showError(model, product,
                    "Danh mục không tồn tại hoặc đã ngừng hoạt động.");
        }

        // 4. Kiểm tra giá
        if (product.getPrice() == null
                || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return showError(model, product,
                    "Giá sản phẩm không hợp lệ.");
        }

        String savedImagePath = null;

        try {
            // 5. Xử lý ảnh nếu người dùng chọn ảnh
            if (imageFile != null && !imageFile.isEmpty()) {

                if (imageFile.getSize() > 5L * 1024 * 1024) {
                    return showError(model, product,
                            "Ảnh không được vượt quá 5 MB.");
                }

                String contentType = imageFile.getContentType();
                String extension;

                if ("image/jpeg".equalsIgnoreCase(contentType)) {
                    extension = ".jpg";
                } else if ("image/png".equalsIgnoreCase(contentType)) {
                    extension = ".png";
                } else if ("image/webp".equalsIgnoreCase(contentType)) {
                    extension = ".webp";
                } else {
                    return showError(model, product,
                            "Ảnh phải có định dạng JPG, PNG hoặc WEBP.");
                }

                Files.createDirectories(uploadDir);

                String fileName = UUID.randomUUID() + extension;
                Path target = uploadDir.resolve(fileName).normalize();

                if (!target.startsWith(uploadDir)) {
                    return showError(model, product,
                            "Đường dẫn lưu ảnh không hợp lệ.");
                }

                imageFile.transferTo(target);
                savedImagePath = target.toString();

                product.setImageUrl("/admin/products/image/" + fileName);
            }

            // 6. Chuẩn hóa dữ liệu trước khi lưu
            product.setId(null);
            product.setName(name);

            if (product.getDescription() != null) {
                product.setDescription(product.getDescription().trim());
            }

            // 7. Lưu sản phẩm
            productRepository.saveAndFlush(product);

            redirectAttributes.addFlashAttribute(
                    "productAddSuccess",
                    "Thêm sản phẩm thành công!"
            );
            redirectAttributes.addFlashAttribute("productAdded", true);

            return "redirect:/admin/products/add";

        } catch (IOException | RuntimeException e) {

            // Xóa ảnh nếu lưu sản phẩm thất bại
            if (savedImagePath != null) {
                try {
                    Files.deleteIfExists(Path.of(savedImagePath));
                } catch (IOException ignored) {
                    // Không làm mất thông báo lỗi chính
                }
            }

            model.addAttribute(
                    "errorMessage",
                    "Không thể lưu sản phẩm. Vui lòng kiểm tra log lỗi trong NetBeans."
            );

            loadCategories(model);
            return "admin-product-add";
        }
    }

    // Trả ảnh đã lưu về trình duyệt
    @GetMapping("/image/{fileName:.+}")
    public ResponseEntity<Resource> getProductImage(
            @PathVariable String fileName) {

        try {
            if (fileName.contains("/")
                    || fileName.contains("\\")
                    || fileName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            Path file = uploadDir.resolve(fileName).normalize();

            if (!file.startsWith(uploadDir)
                    || !Files.isRegularFile(file)
                    || !Files.isReadable(file)) {
                return ResponseEntity.notFound().build();
            }

            String lowerName = fileName.toLowerCase(Locale.ROOT);
            String contentType;

            if (lowerName.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (lowerName.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (IOException | RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Hiển thị lỗi và giữ lại dữ liệu đã nhập
    private String showError(
            Model model,
            Product product,
            String message) {

        model.addAttribute("productForm", product);
        model.addAttribute("errorMessage", message);
        loadCategories(model);

        return "admin-product-add";
    }

    // Chỉ lấy danh mục đang hoạt động
    private void loadCategories(Model model) {
        List<FoodCategory> categories =
                categoryRepository.findAllByOrderByIdDesc()
                        .stream()
                        .filter(FoodCategory::isActive)
                        .toList();

        model.addAttribute("categories", categories);
    }
}