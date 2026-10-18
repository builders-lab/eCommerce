package com.ecommerce.platform.modules.checkout.payment.loadtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.loadtest-payment")
public record LoadTestPaymentProperties(
        @DefaultValue("50") long delayMs,
        @DefaultValue("0") double failureRate,
        @DefaultValue("loadtest-secret") String webhookSecret,
        @DefaultValue("http://localhost:5173/checkout/loadtest") String checkoutBaseUrl
) {
}
