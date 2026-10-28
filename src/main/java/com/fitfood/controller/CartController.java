package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.auth.repository.CartItemRepository;
import com.fitfood.auth.repository.CartRepository;
import com.fitfood.category.Cart;
import com.fitfood.category.CartItem;
import com.fitfood.category.Product;
import com.fitfood.category.ProductRepository;
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

import java.math.BigDecimal;
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

    // Xem giỏ hàng
    @GetMapping
    public String viewCart(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        model.addAttribute("cart", cart);

        BigDecimal cartTotal = BigDecimal.ZERO;
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                if (item.getUnitPrice() != null) {
                    cartTotal = cartTotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        model.addAttribute("cartTotal", cartTotal);
        return "v2/cart";
    }

    // Thêm món ăn vào giỏ kèm thông báo thành công
    @PostMapping("/add")
    public String addToCart(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam("productId") Long productId,
                            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
                            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (user != null && product != null && quantity > 0) {
            Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
                Cart newCart = new Cart();
                newCart.setUser(user);
                return cartRepository.save(newCart);
            });

            Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);
            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + quantity);
                cartItemRepository.save(item);
            } else {
                CartItem newItem = new CartItem();
                newItem.setCart(cart);
                newItem.setProduct(product);
                newItem.setQuantity(quantity);
                newItem.setUnitPrice(product.getPrice());
                cartItemRepository.save(newItem);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Thêm món vào giỏ hàng thành công!");
        }
        return "redirect:/menu";
    }

    // Tăng / giảm số lượng (+ / -)
    @PostMapping("/update-quantity")
    public String updateQuantity(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam("itemId") Long itemId,
                                 @RequestParam("change") int change,
                                 RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
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
            return "redirect:/login";
        }
        cartItemRepository.deleteById(itemId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa món khỏi giỏ hàng thành công!");
        return "redirect:/cart";
    }
}