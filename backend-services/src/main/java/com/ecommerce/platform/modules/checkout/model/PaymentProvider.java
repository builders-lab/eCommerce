package com.ecommerce.platform.modules.checkout.model;

public enum PaymentProvider {
    STRIPE("stripe"),
    RAZORPAY("razorpay"),
    LOADTEST("loadtest"),
    PAYPAL("paypal");

    private final String configKey;

    PaymentProvider(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static PaymentProvider fromConfigValue(String value) {
        if (value == null || value.isBlank()) {
            return STRIPE;
        }
        String normalized = value.trim().toLowerCase();
        for (PaymentProvider provider : values()) {
            if (provider.configKey.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported payment gateway: " + value);
    }
}
