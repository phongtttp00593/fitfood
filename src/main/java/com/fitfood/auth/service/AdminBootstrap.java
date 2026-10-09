package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.UserRole;
import com.fitfood.auth.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    CommandLineRunner initAdmin(AppUserRepository users, PasswordEncoder encoder,
        @Value("${fitfood.bootstrap.admin-email:}") String adminEmail,
        @Value("${fitfood.bootstrap.admin-password:}") String adminPassword) {
        return args -> {
            String email = AccountService.normalizeEmail(adminEmail);
            if (email.isBlank() || adminPassword.isBlank()) {
                log.info("Chua cau hinh admin bootstrap. Xem README_AUTH.txt");
                return;
            }
            if (!AccountService.validEmail(email) || !AccountService.validPassword(adminPassword)) {
                log.warn("Email/mat khau admin bootstrap khong hop le. Khong tao admin.");
                return;
            }
            if (users.existsByEmailIgnoreCase(email)) {
                log.info("Tai khoan admin bootstrap da ton tai, khong ghi de mat khau/quyen.");
                return;
            }
            AppUser admin = new AppUser();
            admin.setEmail(email);
            admin.setFullName("Quản trị FITFOOD");
            admin.setRole(UserRole.ADMIN);
            admin.setPasswordHash(encoder.encode(adminPassword));
            users.save(admin);
            log.info("Da tao tai khoan quan tri FITFOOD tu cau hinh bootstrap.");
        };
    }
}
