package com.ecommerce.platform.modules.cart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CartItemDiscountDto(
        Long id,
        String description,
        BigDecimal percentage,
        LocalDate startDate,
        LocalDate endDate
) {
}
