package com.fitfood.controller;

import com.fitfood.auth.service.AccountService;
import com.fitfood.auth.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {
    private final AccountService accounts;
    private final PasswordResetService resets;
    private final String mailUsername;

    public AuthController(AccountService accounts, PasswordResetService resets,
        @Value("${spring.mail.username:}") String mailUsername) {
        this.accounts = accounts;
        this.resets = resets;
        this.mailUsername = mailUsername;
    }

    @GetMapping("/dang-nhap")
    public String login(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return admin ? "redirect:/admin" : "redirect:/";
        }
        return "login";
    }

    @GetMapping("/dang-ky")
    public String registerPage(){ return "register"; }

    @PostMapping("/dang-ky")
    public String register(@RequestParam String fullName,
        @RequestParam String email, @RequestParam String password,
        @RequestParam String confirmPassword, Model model) {
        try {
            accounts.register(fullName, email, password, confirmPassword);
            return "redirect:/dang-nhap?registered";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("fullName", fullName);
            model.addAttribute("email", email);
            return "register";
        }
    }

    @GetMapping("/quen-mat-khau")
    public String forgot(){ return "forgot-password"; }

    @PostMapping("/quen-mat-khau")
    public String forgotSend(@RequestParam String email, Model model) {
        if (mailUsername == null || mailUsername.isBlank()) {
            model.addAttribute("error", "Chưa cấu hình SMTP. Hãy thiết lập email gửi thư theo README_AUTH.txt.");
            return "forgot-password";
        }
        resets.requestReset(email);
        model.addAttribute("notice", "Nếu email này đã đăng ký FITFOOD, bạn sẽ nhận được liên kết đặt lại mật khẩu (có hiệu lực 15 phút). Hãy kiểm tra cả thư mục Spam.");
        return "forgot-password";
    }

    @GetMapping("/dat-lai-mat-khau")
    public String resetForm(@RequestParam(required=false) String token, Model model) {
        boolean valid = resets.isValidToken(token);
        model.addAttribute("validToken", valid);
        if (valid) model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/dat-lai-mat-khau")
    public String resetSubmit(@RequestParam String token,
        @RequestParam String password, @RequestParam String confirmPassword, Model model) {
        if (resets.resetPassword(token, password, confirmPassword)) {
            return "redirect:/dang-nhap?reset";
        }
        model.addAttribute("validToken", resets.isValidToken(token));
        model.addAttribute("token", token);
        model.addAttribute("error", "Mật khẩu không hợp lệ, xác nhận không khớp hoặc liên kết đã hết hạn. Mật khẩu cần ít nhất 8 ký tự.");
        return "reset-password";
    }

    @GetMapping("/403")
    public String forbidden(){ return "access-denied"; }
}
