package com.ecommerce.platform.modules.checkout.payment.core;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        PaymentProvider provider,
        PaymentStatus status,
        String checkoutUrl,
        String providerReferenceId,
        String paymentReferenceId,
        Instant expiresAt,
        String message
) {
}
