package com.ecommerce.platform.modules.checkout.dto;

public record CheckoutStatusResponse(
        Long orderId,
        String status,
        String paymentStatus,
        String message
) {
}
