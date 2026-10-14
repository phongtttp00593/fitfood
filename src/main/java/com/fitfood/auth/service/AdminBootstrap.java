package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.UserRole;
import com.fitfood.auth.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {

    // Thay 2 dong duoi neu muon doi tai khoan Admin ngay trong NetBeans.
    private static final String ADMIN_EMAIL = "admin@fitfood.local";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Bean
    CommandLineRunner createOrUpdateAdmin(AppUserRepository users,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            AppUser admin = users.findByEmailIgnoreCase(ADMIN_EMAIL)
                    .orElseGet(AppUser::new);

            admin.setEmail(ADMIN_EMAIL);
            admin.setFullName("Quản trị FITFOOD");
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);

            // Chi ma hoa va cap nhat mat khau khi can.
            if (admin.getPasswordHash() == null
                    || !passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
            }

            users.saveAndFlush(admin);
            System.out.println("FITFOOD: Tai khoan Admin da san sang: " + ADMIN_EMAIL);
        };
    }
}
