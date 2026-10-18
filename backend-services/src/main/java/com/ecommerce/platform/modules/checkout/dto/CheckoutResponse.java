package com.ecommerce.platform.modules.checkout.dto;

public record CheckoutResponse(
        Long orderId,
        String status,
        String checkoutUrl,
        String expiresAt
) {
}
