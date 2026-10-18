package com.ecommerce.platform.modules.checkout.payment.stripe;

import java.time.Instant;

public record StripeCheckoutSession(
        String sessionId,
        String checkoutUrl,
        String paymentIntentId,
        Instant expiresAt
) {
}
