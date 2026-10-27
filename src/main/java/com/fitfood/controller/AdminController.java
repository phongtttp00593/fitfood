
package com.fitfood.controller;

import com.fitfood.category.FoodCategory;
import com.fitfood.category.FoodCategoryRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductRepository productRepository;
    private final FoodCategoryRepository categoryRepository;

    public AdminController(
            ProductRepository productRepository,
            FoodCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String admin() {
        return "admin";
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    @GetMapping("/products")
    public String products(Model model) {

        List<Product> products = productRepository.findAll(
                Sort.by(Sort.Direction.DESC, "id"));

        List<FoodCategory> categories = categoryRepository.findAll();

        Map<Long, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(
                        FoodCategory::getId,
                        FoodCategory::getName,
                        (first, second) -> first));

        long activeCount = products.stream()
                .filter(Product::isActive)
                .count();

        long inactiveCount = products.size() - activeCount;

        model.addAttribute("products", products);
        model.addAttribute("categoryNames", categoryNames);
        model.addAttribute("totalProducts", products.size());
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("categoryCount", categories.size());

        // Trả về giao diện danh sách sản phẩm hiện có.
        return "admin-products";
    }
}
