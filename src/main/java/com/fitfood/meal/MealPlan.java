package com.fitfood.meal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fitfood_meal_plans")
public class MealPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "description", length = 500)
    private String description;
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;
    @Column(name = "number_of_days")
    private Integer numberOfDays;
    @Column(name = "available_from")
    private LocalDate availableFrom;
    @Column(name = "active", nullable = false)
    private boolean active;

    protected MealPlan() { }

    public MealPlan(String name, String description, BigDecimal price, Integer numberOfDays,
            LocalDate availableFrom, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.numberOfDays = numberOfDays;
        this.availableFrom = availableFrom;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Integer getNumberOfDays() { return numberOfDays; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public boolean isActive() { return active; }
}
