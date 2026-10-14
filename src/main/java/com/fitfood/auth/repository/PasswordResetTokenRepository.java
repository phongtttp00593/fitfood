package com.fitfood.auth.repository;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    Optional<PasswordResetToken> findTopByUserOrderByCreatedAtDesc(AppUser user);
    void deleteByUser(AppUser user);
}
