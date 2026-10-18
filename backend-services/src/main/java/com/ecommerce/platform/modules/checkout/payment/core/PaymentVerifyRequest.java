package com.ecommerce.platform.modules.checkout.payment.core;

public record PaymentVerifyRequest(
        String payload,
        String signature
) {
}
