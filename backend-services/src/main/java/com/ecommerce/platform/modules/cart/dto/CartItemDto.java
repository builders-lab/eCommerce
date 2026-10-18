package com.ecommerce.platform.modules.cart.dto;

import java.math.BigDecimal;

public record CartItemDto(
        Long id,
        String title,
        BigDecimal price,
        BigDecimal finalPrice,
        String imageUrl,
        int quantity,
        CartItemDiscountDto productDiscount,
        BigDecimal userDiscountPercentage,
        BigDecimal employeeDiscountPercentage,
        BigDecimal totalDiscountPercentage
) {
}
