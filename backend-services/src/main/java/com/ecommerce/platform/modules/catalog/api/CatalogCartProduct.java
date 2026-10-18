package com.ecommerce.platform.modules.catalog.api;

import java.math.BigDecimal;

public record CatalogCartProduct(
        Long id,
        String name,
        BigDecimal price
) {
}
