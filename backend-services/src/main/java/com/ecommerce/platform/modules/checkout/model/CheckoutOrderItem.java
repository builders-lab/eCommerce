package com.ecommerce.platform.modules.checkout.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "checkout_order_items", indexes = {
        @Index(name = "idx_checkout_order_items_order", columnList = "order_id")
})
public class CheckoutOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CheckoutOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String title;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "final_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalUnitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal productDiscountPercentage = BigDecimal.ZERO;

    @Column(name = "user_discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal userDiscountPercentage = BigDecimal.ZERO;

    @Column(name = "employee_discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeDiscountPercentage = BigDecimal.ZERO;

    @Column(name = "total_discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalDiscountPercentage = BigDecimal.ZERO;

    public Long getId() {
        return id;
    }

    public CheckoutOrder getOrder() {
        return order;
    }

    public void setOrder(CheckoutOrder order) {
        this.order = order;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getFinalUnitPrice() {
        return finalUnitPrice;
    }

    public void setFinalUnitPrice(BigDecimal finalUnitPrice) {
        this.finalUnitPrice = finalUnitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getProductDiscountPercentage() {
        return productDiscountPercentage;
    }

    public void setProductDiscountPercentage(BigDecimal productDiscountPercentage) {
        this.productDiscountPercentage = productDiscountPercentage;
    }

    public BigDecimal getUserDiscountPercentage() {
        return userDiscountPercentage;
    }

    public void setUserDiscountPercentage(BigDecimal userDiscountPercentage) {
        this.userDiscountPercentage = userDiscountPercentage;
    }

    public BigDecimal getEmployeeDiscountPercentage() {
        return employeeDiscountPercentage;
    }

    public void setEmployeeDiscountPercentage(BigDecimal employeeDiscountPercentage) {
        this.employeeDiscountPercentage = employeeDiscountPercentage;
    }

    public BigDecimal getTotalDiscountPercentage() {
        return totalDiscountPercentage;
    }

    public void setTotalDiscountPercentage(BigDecimal totalDiscountPercentage) {
        this.totalDiscountPercentage = totalDiscountPercentage;
    }
}
