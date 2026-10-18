package com.ecommerce.platform.modules.checkout.payment.core;

import java.util.List;

public record PaymentRequest(
        Long orderId,
        String currency,
        List<PaymentLineItem> lineItems,
        String idempotencyKey
) {
}
