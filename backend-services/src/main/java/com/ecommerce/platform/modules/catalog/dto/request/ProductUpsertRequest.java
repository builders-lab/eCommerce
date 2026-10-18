package com.ecommerce.platform.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProductUpsertRequest(
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotEmpty List<String> images
) {
}
