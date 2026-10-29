package com.fitfood.meal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "fitfood_customer_meal_plan_items", uniqueConstraints = @UniqueConstraint(
        name = "uq_customer_plan_slot", columnNames = {"user_id", "meal_plan_id", "slot_product_id"}))
public class CustomerMealPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "meal_plan_id", nullable = false)
    private Long mealPlanId;

    @Column(name = "slot_product_id", nullable = false)
    private Long slotProductId;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}