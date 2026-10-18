package com.ecommerce.platform.modules.cart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CartItemView(
        Long productId,
        String productName,
        BigDecimal productPrice,
        int quantity,
        Long selectedDiscountId,
        String selectedDiscountDescription,
        BigDecimal selectedDiscountPercentage,
        LocalDate selectedDiscountStartDate,
        LocalDate selectedDiscountEndDate
) {
}
