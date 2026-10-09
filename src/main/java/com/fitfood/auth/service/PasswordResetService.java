package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.PasswordResetToken;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.auth.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    private final String mailUser;
    private final String baseUrl;

    public PasswordResetService(AppUserRepository users,
        PasswordResetTokenRepository tokens,
        PasswordEncoder encoder,
        JavaMailSender mailSender,
        @Value("${spring.mail.username:}") String mailUser,
        @Value("${fitfood.base-url:http://localhost:8080}") String baseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.mailUser = mailUser;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String emailInput) {
        String email = AccountService.normalizeEmail(emailInput);
        if (!AccountService.validEmail(email)) return;
        Optional<AppUser> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty() || !found.get().isEnabled()) return;
        AppUser user = found.get();
        Instant now = Instant.now();
        Optional<PasswordResetToken> last = tokens.findTopByUserOrderByCreatedAtDesc(user);
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minusSeconds(60))) return;
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        PasswordResetToken entry = new PasswordResetToken();
        entry.setUser(user);
        entry.setTokenHash(sha256(rawToken)); // Chỉ lưu hash, không lưu token gốc
        entry.setCreatedAt(now);
        entry.setExpiresAt(now.plus(TTL));
        tokens.saveAndFlush(entry);
        String url = baseUrl + "/dat-lai-mat-khau?token=" + rawToken;
        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(user.getEmail());
        emailMessage.setFrom(mailUser);
        emailMessage.setSubject("FITFOOD - Dat lai mat khau");
        emailMessage.setText("Xin chao " + user.getFullName() + ",\n\n"
            + "Ban da yeu cau dat lai mat khau FITFOOD. Truy cap lien ket sau:\n"
            + url + "\n\nLien ket co hieu luc 15 phut va chi dung duoc mot lan. "
            + "Neu ban khong yeu cau, hay bo qua email nay.\nFITFOOD");
        try {
            mailSender.send(emailMessage);
        } catch (RuntimeException e) {
            log.error("Khong gui duoc email dat lai mat khau; kiem tra cau hinh SMTP", e);
            // Không tiết lộ tài khoản có tồn tại hay không qua UI.
        }
    }

    @Transactional(readOnly = true)
    public boolean isValidToken(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) return false;
        return tokens.findByTokenHash(sha256(raw))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    @Transactional
    public boolean resetPassword(String raw, String password, String confirm) {
        if (!AccountService.validPassword(password) || !password.equals(confirm)) return false;
        if (raw == null || raw.isBlank() || raw.length() > 200) return false;
        Optional<PasswordResetToken> found = tokens.findByTokenHash(sha256(raw));
        if (found.isEmpty()) return false;
        PasswordResetToken token = found.get();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) return false;
        AppUser user = token.getUser();
        user.setPasswordHash(encoder.encode(password));
        users.save(user);
        // Vô hiệu toàn bộ các link đặt lại mật khẩu cũ của tài khoản này.
        tokens.deleteByUser(user);
        return true;
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
