package com.ecommerce.platform.modules.checkout.payment.core;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;

public interface PaymentService {

    PaymentProvider getProvider();

    PaymentResponse createPayment(PaymentRequest request);

    PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request);
}
