package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.UserRole;
import com.fitfood.auth.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    public AccountService(AppUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validEmail(String email) {
        return email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    public static boolean validPassword(String password) {
        return password != null && password.length() >= 8 && password.length() <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Transactional
    public void register(String name, String emailInput, String password, String confirmPassword) {
        String email = normalizeEmail(emailInput);
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() < 2 || cleanName.length() > 120) {
            throw new IllegalArgumentException("Họ tên phải có từ 2 đến 120 ký tự.");
        }
        if (!validEmail(email)) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }
        if (!validPassword(password)) {
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 64 ký tự.");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã được đăng ký.");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(cleanName);
        user.setPasswordHash(passwords.encode(password));
        user.setRole(UserRole.USER); // ĐĂNG KÝ CHỈ ĐƯỢC TẠO USER
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Email đã được đăng ký.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String inputEmail) throws UsernameNotFoundException {
        AppUser account = users.findByEmailIgnoreCase(normalizeEmail(inputEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không đúng"));
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .disabled(!account.isEnabled())
                .build();
    }
}
