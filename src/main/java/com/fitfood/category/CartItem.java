package com.fitfood.category;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "fitfood_cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Nationalized
    @Column(name = "meal_plan_name", length = 180)
    private String mealPlanName;

    // Ghi chú riêng của khách cho món ăn; NVARCHAR để lưu tiếng Việt có dấu.
    @Nationalized
    @Column(name = "food_note", length = 500)
    private String foodNote;


    public CartItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getMealPlanName() { return mealPlanName; }
    public void setMealPlanName(String mealPlanName) { this.mealPlanName = mealPlanName; }

    public String getFoodNote() { return foodNote; }
    public void setFoodNote(String foodNote) { this.foodNote = foodNote; }
}