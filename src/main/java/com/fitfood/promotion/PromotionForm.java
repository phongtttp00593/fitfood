/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitfood.promotion;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public class PromotionForm {

    private Long id;

    @NotBlank(message = "Vui lòng nhập mã khuyến mãi")
    @Size(max = 50, message = "Mã tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên khuyến mãi")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @DecimalMin(value = "0.0",
            message = "Phần trăm giảm không được âm")
    @DecimalMax(value = "100.0",
            message = "Phần trăm giảm tối đa 100%")
    private BigDecimal percentDiscount;

    @DecimalMin(value = "0.0",
            message = "Số tiền giảm không được âm")
    private BigDecimal amountDiscount;

    @NotNull(message = "Vui lòng chọn ngày bắt đầu")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;

    @NotNull(message = "Vui lòng chọn ngày kết thúc")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endsAt;

    @NotNull(message = "Vui lòng nhập đơn tối thiểu")
    @DecimalMin(value = "0.0",
            message = "Đơn tối thiểu không được âm")
    private BigDecimal minimumOrder = BigDecimal.ZERO;

    @DecimalMin(value = "0.0",
            message = "Giảm tối đa không được âm")
    private BigDecimal maximumDiscount;

    private boolean active = true;

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
