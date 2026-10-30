package com.fitfood.controller;

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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.*;

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
        } catch (Exception ignored) {
        }
    }

    // Xem giỏ hàng
    @GetMapping
    public String viewCart(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/dang-nhap";
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/dang-nhap";
        }

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        model.addAttribute("cart", cart);

        // Nhóm các dòng món thuộc cùng một gói thành một thẻ gói trong giỏ hàng.
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
                    // Gói được hiển thị như một dòng giỏ hàng; ghi chú món lấy từ cấu hình gói.
                    List<Map<String, Object>> notes = jdbc.query(
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

        // Lấy sẵn thông tin người nhận mặc định từ hồ sơ khách hàng
        String defaultPhone = "";
        String defaultAddress = "";
        List<Map<String, Object>> profiles = jdbc.queryForList(
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

        return "v2/cart";
    }

    // Thêm món ăn vào giỏ kèm thông báo thành công
    @PostMapping("/add")
    public String addToCart(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam("productId") Long productId,
                            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
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

            Optional<CartItem> existingItem = cartItemRepository
                    .findFirstByCartIdAndProductIdAndMealPlanNameIsNullOrderByIdAsc(cart.getId(), productId);
            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + quantity);
                if (foodNote != null && !foodNote.isBlank()) item.setFoodNote(foodNote.trim());
                cartItemRepository.save(item);
            } else {
                CartItem newItem = new CartItem();
                newItem.setCart(cart);
                newItem.setProduct(product);
                newItem.setQuantity(quantity);
                newItem.setUnitPrice(product.getPrice());
                if (foodNote != null && !foodNote.isBlank()) newItem.setFoodNote(foodNote.trim());
                cartItemRepository.save(newItem);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Thêm món vào giỏ hàng thành công!");
        }
        return "redirect:/menu";
    }


    /** Thêm cả gói ăn vào giỏ; món trong gói lấy từ cấu hình Admin, USER không tự chọn món. */
    @PostMapping("/add-meal-plan")
    @Transactional
    public String addMealPlanToCart(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam("mealPlanId") Long mealPlanId,
                                    RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/dang-nhap";
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/dang-nhap";

        var plans = jdbc.query("SELECT id, name, price FROM dbo.fitfood_meal_plans WHERE id = ? AND active = 1",
                (rs, rowNum) -> new Object[]{rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price")}, mealPlanId);
        if (plans.isEmpty()) {
            redirectAttributes.addFlashAttribute("mealPlanError", "Gói ăn không còn mở bán.");
            return "redirect:/menu#goi-an";
        }
        Object[] plan = plans.get(0);
        String planName = (String) plan[1];
        BigDecimal planPrice = (BigDecimal) plan[2];
        var dishes = jdbc.query("""
                SELECT p.id AS product_id, mpi.quantity
                FROM dbo.fitfood_meal_plan_items mpi
                JOIN dbo.fitfood_products p ON p.id = mpi.product_id
                WHERE mpi.meal_plan_id = ? AND p.active = 1
                ORDER BY p.id
                """, (rs, rowNum) -> new long[]{rs.getLong("product_id"), rs.getInt("quantity")}, mealPlanId);
        if (dishes.isEmpty()) {
            redirectAttributes.addFlashAttribute("mealPlanError", "Gói ăn chưa có món. Vui lòng liên hệ Admin cập nhật thực đơn.");
            return "redirect:/menu#goi-an";
        }
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart created = new Cart(); created.setUser(user); return cartRepository.save(created);
        });
        Product representativeProduct = productRepository.findById(dishes.get(0)[0]).orElse(null);
        if (representativeProduct == null) {
            redirectAttributes.addFlashAttribute("mealPlanError", "Không tìm thấy món tham chiếu của gói ăn.");
            return "redirect:/menu#goi-an";
        }
        Optional<CartItem> existingPlanItem = cartItemRepository
                .findFirstByCartIdAndMealPlanNameOrderByIdAsc(cart.getId(), planName);
        if (existingPlanItem.isPresent()) {
            CartItem packageItem = existingPlanItem.get();
            packageItem.setQuantity(packageItem.getQuantity() + 1);
            packageItem.setUnitPrice(planPrice);
            cartItemRepository.save(packageItem);
        } else {
            CartItem packageItem = new CartItem();
            packageItem.setCart(cart);
            packageItem.setProduct(representativeProduct);
            packageItem.setQuantity(1);
            packageItem.setUnitPrice(planPrice);
            packageItem.setMealPlanName(planName);
            cartItemRepository.save(packageItem);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm gói “" + planName + "” vào giỏ hàng.");
        return "redirect:/cart";
    }

    // Tăng / giảm số lượng (+ / -)
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

    // Xóa sản phẩm khỏi giỏ hàng kèm thông báo
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
