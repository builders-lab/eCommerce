package com.ecommerce.platform.modules.checkout.payment.core;

import java.math.BigDecimal;

public record PaymentLineItem(
        String name,
        BigDecimal unitAmount,
        int quantity
) {
}
