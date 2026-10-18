package com.ecommerce.platform.modules.reviews.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddReviewRequest(
        @Min(1) @Max(5) int rating,
        @NotBlank String comment
) {
}
