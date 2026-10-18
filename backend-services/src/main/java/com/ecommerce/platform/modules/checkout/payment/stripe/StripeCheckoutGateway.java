package com.ecommerce.platform.modules.checkout.payment.stripe;

import java.util.List;

public interface StripeCheckoutGateway {
    StripeCheckoutSession createHostedCheckoutSession(Long orderId,
                                                      String currency,
                                                      List<StripeCheckoutLineItem> lineItems,
                                                      String idempotencyKey);
}
