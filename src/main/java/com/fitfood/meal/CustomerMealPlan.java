package com.fitfood.meal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitfood_customer_meal_plans")
public class CustomerMealPlan {
    @Id @Column(name = "user_id")
    private Long userId;
    @Column(name = "meal_plan_id", nullable = false)
    private Long mealPlanId;
    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt = LocalDateTime.now();

    protected CustomerMealPlan() { }
    public CustomerMealPlan(Long userId, Long mealPlanId) {
        this.userId = userId;
        this.mealPlanId = mealPlanId;
        this.selectedAt = LocalDateTime.now();
    }
    public Long getUserId() { return userId; }
    public Long getMealPlanId() { return mealPlanId; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
}
