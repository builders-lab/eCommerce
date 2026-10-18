package com.ecommerce.platform.modules.catalog.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountResponse(
        Long id,
        String description,
        BigDecimal percentage,
        LocalDate startDate,
        LocalDate endDate
) {
}
