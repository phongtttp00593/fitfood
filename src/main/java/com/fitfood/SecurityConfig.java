package com.fitfood;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/", "/menu", "/product", "/cart", "/dang-nhap",
                    "/dang-ky", "/quen-mat-khau", "/dat-lai-mat-khau",
                    "/403", "/css/**", "/images/**", "/js/**", "/favicon.ico", "/error")
                    .permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .loginProcessingUrl("/dang-nhap")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    boolean admin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    response.sendRedirect(request.getContextPath() + (admin ? "/admin" : "/"));
                })
                .failureUrl("/dang-nhap?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/dang-xuat")
                .logoutSuccessUrl("/dang-nhap?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .exceptionHandling(ex -> ex.accessDeniedPage("/403"));
        // Giữ CSRF mặc định BẬT. Mọi form POST cần chứa token CSRF.
        return http.build();
    }
}
