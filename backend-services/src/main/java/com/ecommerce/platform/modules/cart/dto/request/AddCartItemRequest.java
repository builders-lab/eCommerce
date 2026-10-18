package com.ecommerce.platform.modules.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull Long productId,
        @Positive int quantity,
        @Min(0) Long discountId
) {
}
