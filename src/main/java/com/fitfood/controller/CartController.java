
package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.cart.model.Cart;
import com.fitfood.cart.model.CartItem;
import com.fitfood.cart.repository.CartItemRepository;
import com.fitfood.cart.repository.CartRepository;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;

    public CartController(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            AppUserRepository appUserRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
    }

    // Lấy tài khoản đang đăng nhập
    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        return appUserRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElse(null);
    }

    // Tìm giỏ hàng của tài khoản, nếu chưa có thì tạo mới
    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUserId(userId);
                    return cartRepository.save(cart);
                });
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getNumberInstance(
                new Locale("vi", "VN")
        ).format(amount) + "đ";
    }

    // Xem giỏ hàng
    @GetMapping("/cart")
    public String viewCart(
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        AppUser user = getCurrentUser(authentication);

        if (user == null) {
            redirectAttributes.addFlashAttribute(
                    "loginMessage",
                    "Vui lòng đăng nhập để xem giỏ hàng."
            );
            return "redirect:/dang-nhap";
        }

        Cart cart = getOrCreateCart(user.getId());

        List<CartItem> items =
                cartItemRepository.findByCart_IdOrderByIdDesc(cart.getId());

        List<Map<String, Object>> cartItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int cartCount = 0;

        for (CartItem item : items) {
            Product product = item.getProduct();

            BigDecimal lineTotal = item.getLineTotal();
            subtotal = subtotal.add(lineTotal);
            cartCount += item.getQuantity();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_id", item.getId());
            row.put("product_id", product.getId());
            row.put("name", product.getName());
            row.put("image_url", product.getImageUrl());
            row.put("quantity", item.getQuantity());
            row.put("unitPriceLabel", formatMoney(item.getUnitPrice()));
            row.put("lineTotalLabel", formatMoney(lineTotal));

            cartItems.add(row);
        }

        BigDecimal shippingFee = cartItems.isEmpty()
                ? BigDecimal.ZERO
                : new BigDecimal("15000");

        BigDecimal total = subtotal.add(shippingFee);

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("subtotalLabel", formatMoney(subtotal));
        model.addAttribute("shippingLabel", formatMoney(shippingFee));
        model.addAttribute("totalLabel", formatMoney(total));

        return "cart";
    }

    // Thêm sản phẩm
    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        AppUser user = getCurrentUser(authentication);

        if (user == null) {
            redirectAttributes.addFlashAttribute(
                    "loginMessage",
                    "Vui lòng đăng nhập để thêm món vào giỏ hàng."
            );
            return "redirect:/dang-nhap";
        }

        if (quantity < 1 || quantity > 99) {
            redirectAttributes.addFlashAttribute(
                    "cartMessage",
                    "Số lượng phải từ 1 đến 99."
            );
            return "redirect:/product?id=" + productId;
        }

        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElse(null);

        if (product == null) {
            redirectAttributes.addFlashAttribute(
                    "cartMessage",
                    "Sản phẩm không tồn tại hoặc đã ngừng bán."
            );
            return "redirect:/menu";
        }

        Cart cart = getOrCreateCart(user.getId());

        CartItem item = cartItemRepository
                .findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElse(null);

        if (item != null) {
            int newQuantity = item.getQuantity() + quantity;

            if (newQuantity > 99) {
                redirectAttributes.addFlashAttribute(
                        "cartMessage",
                        "Mỗi món chỉ được đặt tối đa 99 phần."
                );
                return "redirect:/cart";
            }

            item.setQuantity(newQuantity);
            item.setUnitPrice(product.getPrice());
        } else {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
        }

        cartItemRepository.save(item);

        redirectAttributes.addFlashAttribute(
                "cartMessage",
                "Đã thêm " + product.getName() + " vào giỏ hàng."
        );

        return "redirect:/cart";
    }

    // Cập nhật số lượng
    @PostMapping("/cart/update")
    public String updateQuantity(
            @RequestParam Long itemId,
            @RequestParam int quantity,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        AppUser user = getCurrentUser(authentication);

        if (user == null) {
            return "redirect:/dang-nhap";
        }

        if (quantity < 1 || quantity > 99) {
            redirectAttributes.addFlashAttribute(
                    "cartMessage",
                    "Số lượng phải từ 1 đến 99."
            );
            return "redirect:/cart";
        }

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElse(null);

        if (cart == null) {
            return "redirect:/cart";
        }

        CartItem item = cartItemRepository
                .findByIdAndCart_Id(itemId, cart.getId())
                .orElse(null);

        if (item != null) {
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return "redirect:/cart";
    }

    // Xóa sản phẩm
    @PostMapping("/cart/remove")
    public String removeItem(
            @RequestParam Long itemId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        AppUser user = getCurrentUser(authentication);

        if (user == null) {
            return "redirect:/dang-nhap";
        }

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElse(null);

        if (cart != null) {
            CartItem item = cartItemRepository
                    .findByIdAndCart_Id(itemId, cart.getId())
                    .orElse(null);

            if (item != null) {
                cartItemRepository.delete(item);
                redirectAttributes.addFlashAttribute(
                        "cartMessage",
                        "Đã xóa sản phẩm khỏi giỏ hàng."
                );
            }
        }

        return "redirect:/cart";
    }
}
