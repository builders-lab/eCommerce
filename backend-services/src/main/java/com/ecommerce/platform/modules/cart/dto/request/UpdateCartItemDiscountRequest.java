package com.ecommerce.platform.modules.cart.dto.request;

import jakarta.validation.constraints.Min;

public record UpdateCartItemDiscountRequest(
        @Min(0) Long discountId
) {
}
