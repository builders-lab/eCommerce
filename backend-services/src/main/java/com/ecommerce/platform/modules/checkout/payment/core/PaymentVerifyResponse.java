package com.ecommerce.platform.modules.checkout.payment.core;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;

public record PaymentVerifyResponse(
        PaymentProvider provider,
        String eventId,
        String eventType,
        boolean supportedEvent,
        String providerReferenceId,
        String paymentReferenceId,
        PaymentStatus paymentStatus,
        String message
) {
}
