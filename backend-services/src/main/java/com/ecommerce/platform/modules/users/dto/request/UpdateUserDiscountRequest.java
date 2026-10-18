package com.ecommerce.platform.modules.users.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateUserDiscountRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal percentage,
        LocalDate startDate,
        LocalDate endDate
) {
}
