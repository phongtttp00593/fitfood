/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


package com.fitfood.promotion;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitfood_promotions", schema = "dbo")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false,
            unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "percent_discount", precision = 5, scale = 2)
    private BigDecimal percentDiscount;

    @Column(name = "amount_discount", precision = 18, scale = 2)
    private BigDecimal amountDiscount;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "minimum_order", nullable = false,
            precision = 18, scale = 2)
    private BigDecimal minimumOrder = BigDecimal.ZERO;

    @Column(name = "maximum_discount", precision = 18, scale = 2)
    private BigDecimal maximumDiscount;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Promotion() {
    }

    // Tự động xác định trạng thái theo thời gian hiện tại
    @Transient
    public String getStatus() {
        if (!active) {
            return "Đã tắt";
        }

        LocalDateTime now = LocalDateTime.now();

        if (startsAt == null || endsAt == null) {
            return "Chưa thiết lập";
        }

        if (now.isBefore(startsAt)) {
            return "Sắp diễn ra";
        }

        if (!now.isBefore(endsAt)) {
            return "Đã kết thúc";
        }

        return "Đang diễn ra";
    }

    // Kiểm tra mã có đang trong thời gian sử dụng hay không
    @Transient
    public boolean isCurrentlyValid() {
        if (!active || startsAt == null || endsAt == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        return !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPercentDiscount() {
        return percentDiscount;
    }

    public void setPercentDiscount(BigDecimal percentDiscount) {
        this.percentDiscount = percentDiscount;
    }

    public BigDecimal getAmountDiscount() {
        return amountDiscount;
    }

    public void setAmountDiscount(BigDecimal amountDiscount) {
        this.amountDiscount = amountDiscount;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public BigDecimal getMinimumOrder() {
        return minimumOrder;
    }

    public void setMinimumOrder(BigDecimal minimumOrder) {
        this.minimumOrder = minimumOrder;
    }

    public BigDecimal getMaximumDiscount() {
        return maximumDiscount;
    }

    public void setMaximumDiscount(BigDecimal maximumDiscount) {
        this.maximumDiscount = maximumDiscount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
