package com.ecommerce.platform.modules.checkout.payment.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(
        @DefaultValue("") String keyId,
        @DefaultValue("") String keySecret,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("http://localhost:5173/checkout/razorpay") String checkoutBaseUrl
) {
}
