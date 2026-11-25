package com.fitfood.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.auth.repository.CartItemRepository;
import com.fitfood.auth.repository.CartRepository;
import com.fitfood.category.Cart;
import com.fitfood.category.CartItem;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @PostConstruct
    public void ensureCartItemsSchema() {
        try {
            jdbc.execute("""
                IF EXISTS (
                    SELECT 1 FROM sys.key_constraints
                    WHERE name = 'UQ_ff_cartitem_product'
                      AND parent_object_id = OBJECT_ID(N'dbo.fitfood_cart_items')
                )
                BEGIN
                    ALTER TABLE dbo.fitfood_cart_items DROP CONSTRAINT UQ_ff_cartitem_product;
                END
                """);
            jdbc.execute("""
                IF EXISTS (
                    SELECT 1 FROM sys.columns c
                    JOIN sys.types t ON c.user_type_id = t.user_type_id
                    WHERE c.object_id = OBJECT_ID(N'dbo.fitfood_cart_items')
                      AND c.name = 'meal_plan_name'
                      AND t.name <> 'nvarchar'
                )
                BEGIN
                    ALTER TABLE dbo.fitfood_cart_items ALTER COLUMN meal_plan_name NVARCHAR(180) NULL;
                END
                """);
            jdbc.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.columns c
                    WHERE c.object_id = OBJECT_ID(N'dbo.fitfood_cart_items')
                      AND c.name = 'variant_name'
                )
                BEGIN
                    ALTER TABLE dbo.fitfood_cart_items ADD variant_name NVARCHAR(200) NULL;
                END
                """);
        } catch (Exception ignored) {
        }
    }

    @GetMapping
public String viewCart(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") String recheckout,
        Model model) {

    if ("1".equals(recheckout)) {
        return "redirect:/cart/checkout?paymentError=1";
    }

    return loadCartPage(userDetails, model, false);
}

@GetMapping("/checkout")
public String viewCheckout(
        @AuthenticationPrincipal UserDetails userDetails,
        Model model) {

    return loadCartPage(userDetails, model, true);
}

private String loadCartPage(
        UserDetails userDetails,
        Model model,
        boolean checkoutPage) {
        if (userDetails == null) {
            return "redirect:/dang-nhap";
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        model.addAttribute("cart", cart);

        List<CartItem> regularCartItems = new ArrayList<>();
        Map<String, Map<String, Object>> groupedPlans = new LinkedHashMap<>();
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                String planName = item.getMealPlanName();
                if (planName == null || planName.isBlank()) {
                    regularCartItems.add(item);
                    continue;
                }
                Map<String, Object> group = groupedPlans.computeIfAbsent(planName, key -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("name", key);
                    value.put("items", new ArrayList<CartItem>());
                    value.put("total", BigDecimal.ZERO);
                    
                    var notes = jdbc.query(
                        "SELECT p.name, mpi.quantity, p.price " +
                        "FROM dbo.fitfood_meal_plans mp " +
                        "JOIN dbo.fitfood_meal_plan_items mpi ON mpi.meal_plan_id = mp.id " +
                        "JOIN dbo.fitfood_products p ON p.id = mpi.product_id " +
                        "WHERE mp.name = ? ORDER BY p.name", (rs, rowNum) -> {
                            Map<String, Object> note = new LinkedHashMap<>();
                            note.put("name", rs.getString("name"));
                            note.put("quantity", rs.getInt("quantity"));
                            note.put("price", rs.getBigDecimal("price"));
                            note.put("subtotal", rs.getBigDecimal("price").multiply(BigDecimal.valueOf(rs.getInt("quantity"))));
                            return note;
                        }, key);
                    value.put("notes", notes);
                    return value;
                });
                @SuppressWarnings("unchecked")
                List<CartItem> planItems = (List<CartItem>) group.get("items");
                planItems.add(item);
                BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                group.put("total", ((BigDecimal) group.get("total")).add(lineTotal));
            }
        }
        model.addAttribute("regularCartItems", regularCartItems);
        model.addAttribute("mealPlanGroups", new ArrayList<>(groupedPlans.values()));

        BigDecimal cartTotal = BigDecimal.ZERO;
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                if (item.getUnitPrice() != null) {
                    cartTotal = cartTotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        model.addAttribute("cartTotal", cartTotal);

        String defaultPhone = "";
        String defaultAddress = "";
        var profiles = jdbc.queryForList(
                "SELECT phone, address FROM dbo.fitfood_customer_profiles WHERE user_id = ?",
                user.getId());
        if (!profiles.isEmpty()) {
            Object p = profiles.get(0).get("phone");
            Object a = profiles.get(0).get("address");
            if (p != null) defaultPhone = p.toString();
            if (a != null) defaultAddress = a.toString();
        }
        model.addAttribute("customerFullName", user.getFullName() == null ? "" : user.getFullName());
        model.addAttribute("customerPhone", defaultPhone);
        model.addAttribute("customerAddress", defaultAddress);

        if (checkoutPage && (cart == null
        || cart.getItems() == null
        || cart.getItems().isEmpty())) {

    return "redirect:/cart";
}

model.addAttribute("checkoutPage", checkoutPage);

return "v2/cart";
    }

    @PostMapping("/add")
    public String addToCart(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam("productId") Long productId,
                            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
                            @RequestParam(value = "variantName", required = false) String variantName,
                            @RequestParam(value = "unitPrice", required = false) BigDecimal unitPrice,
                            @RequestParam(value = "foodNote", required = false) String foodNote,
                            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/dang-nhap";
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (user != null && product != null && quantity > 0) {
            Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
                Cart newCart = new Cart();
                newCart.setUser(user);
                return cartRepository.save(newCart);
            });

            String cleanVariantName = (variantName != null && !variantName.isBlank()) ? variantName.trim() : null;

            // Lấy giá đã tính kèm biến thể từ form, nếu không có thì lấy giá gốc
            BigDecimal finalUnitPrice = (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) 
                                        ? unitPrice 
                                        : product.getPrice();

            // So sánh trùng cả món và cùng chuỗi biến thể
            CartItem matchedItem = null;
            if (cart.getItems() != null) {
                for (CartItem item : cart.getItems()) {
                    if (item.getMealPlanName() == null && item.getProduct().getId().equals(productId)) {
                        boolean sameVariant = (cleanVariantName == null && (item.getVariantName() == null || item.getVariantName().isBlank()))
                                           || (cleanVariantName != null && cleanVariantName.equalsIgnoreCase(item.getVariantName()));
                        if (sameVariant) {
                            matchedItem = item;
                            break;
                        }
                    }
                }
            }

            if (matchedItem != null) {
                matchedItem.setQuantity(matchedItem.getQuantity() + quantity);
                matchedItem.setUnitPrice(finalUnitPrice);
                if (foodNote != null && !foodNote.isBlank()) {
                    matchedItem.setFoodNote(foodNote.trim());
                }
                cartItemRepository.save(matchedItem);
            } else {
                CartItem newItem = new CartItem();
                newItem.setCart(cart);
                newItem.setProduct(product);
                newItem.setQuantity(quantity);
                newItem.setUnitPrice(finalUnitPrice);
                newItem.setVariantName(cleanVariantName);
                if (foodNote != null && !foodNote.isBlank()) {
                    newItem.setFoodNote(foodNote.trim());
                }
                cartItemRepository.save(newItem);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Thêm món vào giỏ hàng thành công!");
        }
        return "redirect:/cart";
    }

    @PostMapping("/update-quantity")
    public String updateQuantity(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam("itemId") Long itemId,
                                 @RequestParam("change") int change,
                                 RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/dang-nhap";
        }

        Optional<CartItem> optItem = cartItemRepository.findById(itemId);
        if (optItem.isPresent()) {
            CartItem item = optItem.get();
            int newQuantity = item.getQuantity() + change;
            if (newQuantity < 1) {
                redirectAttributes.addFlashAttribute("warningMessage", "Không thể trừ thêm! Số lượng tối thiểu là 1.");
            } else {
                item.setQuantity(newQuantity);
                cartItemRepository.save(item);
            }
        }
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeItem(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam("itemId") Long itemId,
                             RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/dang-nhap";
        }
        cartItemRepository.deleteById(itemId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa món khỏi giỏ hàng thành công!");
        return "redirect:/cart";
    }
}