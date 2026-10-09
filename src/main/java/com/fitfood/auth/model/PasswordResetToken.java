package com.fitfood.auth.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fitfood_password_reset_tokens")
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="token_hash", nullable=false, unique=true, length=64)
    private String tokenHash;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false)
    private AppUser user;

    @Column(name="created_at", nullable=false)
    private Instant createdAt;

    @Column(name="expires_at", nullable=false)
    private Instant expiresAt;

    @Column(name="used_at")
    private Instant usedAt;

    public Long getId(){ return id; }
    public String getTokenHash(){ return tokenHash; }
    public void setTokenHash(String value){ this.tokenHash = value; }
    public AppUser getUser(){ return user; }
    public void setUser(AppUser value){ this.user = value; }
    public Instant getCreatedAt(){ return createdAt; }
    public void setCreatedAt(Instant value){ this.createdAt = value; }
    public Instant getExpiresAt(){ return expiresAt; }
    public void setExpiresAt(Instant value){ this.expiresAt = value; }
    public Instant getUsedAt(){ return usedAt; }
    public void setUsedAt(Instant value){ this.usedAt = value; }
}
