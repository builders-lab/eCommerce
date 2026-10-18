package com.ecommerce.platform.modules.catalog.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CatalogDiscount(
        Long id,
        String description,
        BigDecimal percentage,
        LocalDate startDate,
        LocalDate endDate
) {
}
