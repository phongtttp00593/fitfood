package com.fitfood.auth.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fitfood_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Instant getCreatedAt(){ return createdAt; }
    public Long getId(){ return id; }
    public String getEmail(){ return email; }
    public void setEmail(String email){ this.email = email; }
    public String getPasswordHash(){ return passwordHash; }
    public void setPasswordHash(String passwordHash){ this.passwordHash = passwordHash; }
    public String getFullName(){ return fullName; }
    public void setFullName(String fullName){ this.fullName = fullName; }
    public UserRole getRole(){ return role; }
    public void setRole(UserRole role){ this.role = role; }
    public boolean isEnabled(){ return enabled; }
    public void setEnabled(boolean enabled){ this.enabled = enabled; }
}
