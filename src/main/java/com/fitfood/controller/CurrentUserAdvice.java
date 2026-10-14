package com.fitfood.controller;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.UserRole;
import com.fitfood.auth.repository.AppUserRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final AppUserRepository users;

    public CurrentUserAdvice(AppUserRepository users) {
        this.users = users;
    }

    private boolean loggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(Authentication auth) {
        return loggedIn(auth) ? auth.getName() : null;
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication auth) {
        return loggedIn(auth) && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @ModelAttribute("accountInfo")
    public AccountInfo accountInfo(Authentication auth) {
        if (!loggedIn(auth)) return null;
        return users.findByEmailIgnoreCase(auth.getName())
                .map(this::toAccountInfo).orElse(null);
    }

    private AccountInfo toAccountInfo(AppUser user) {
        String name = user.getFullName();
        String initial = name.isBlank() ? "F" : name.substring(0, 1).toUpperCase();
        String role = user.getRole() == UserRole.ADMIN ? "Quản trị viên" : "Khách hàng";
        String since = user.getCreatedAt() == null ? "Chưa có" : DATE_FORMAT.format(user.getCreatedAt());
        return new AccountInfo(name, user.getEmail(), role, initial, since);
    }

    public static class AccountInfo {
        private final String fullName, email, role, initials, joinedDate;

        public AccountInfo(String fullName, String email, String role, String initials, String joinedDate) {
            this.fullName = fullName;
            this.email = email;
            this.role = role;
            this.initials = initials;
            this.joinedDate = joinedDate;
        }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public String getInitials() { return initials; }
        public String getJoinedDate() { return joinedDate; }
    }
}
