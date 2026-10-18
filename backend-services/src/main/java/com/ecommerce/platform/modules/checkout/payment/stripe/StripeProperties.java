package com.ecommerce.platform.modules.checkout.payment.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.stripe")
public record StripeProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("") String publishableKey,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("https://api.stripe.com/v1") String apiBaseUrl,
        @DefaultValue("http://localhost:5173/checkout/success") String successUrl,
        @DefaultValue("http://localhost:5173/checkout/cancel") String cancelUrl,
        @DefaultValue("usd") String currency,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("10000") int readTimeoutMs,
        @DefaultValue("2") int maxAttempts
) {
}
