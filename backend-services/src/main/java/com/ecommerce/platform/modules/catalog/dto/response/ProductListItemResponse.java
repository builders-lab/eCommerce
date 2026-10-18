package com.ecommerce.platform.modules.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductListItemResponse(
        Long id,
        String name,
        String description,
        String category,
        BigDecimal price,
        List<String> images
) {
}
