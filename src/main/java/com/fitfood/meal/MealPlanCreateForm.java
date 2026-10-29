package com.fitfood.meal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MealPlanCreateForm {
    @NotBlank(message = "Vui lòng nhập tên gói ăn.")
    @Size(max = 150, message = "Tên gói ăn tối đa 150 ký tự.")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
    private String description;

    @NotNull(message = "Vui lòng nhập giá gói.")
    @DecimalMin(value = "0.0", message = "Giá gói không được âm.")
    private BigDecimal price;

    @NotNull(message = "Vui lòng nhập thời hạn gói.")
    @Min(value = 1, message = "Thời hạn tối thiểu là 1 ngày.")
    @Max(value = 365, message = "Thời hạn tối đa là 365 ngày.")
    private Integer numberOfDays;

    @NotNull(message = "Vui lòng chọn ngày áp dụng.")
    private LocalDate availableFrom;

    @NotEmpty(message = "Hãy chọn ít nhất một sản phẩm cho gói.")
    private List<Long> productIds = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getNumberOfDays() { return numberOfDays; }
    public void setNumberOfDays(Integer numberOfDays) { this.numberOfDays = numberOfDays; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public List<Long> getProductIds() { return productIds; }
    public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
}
