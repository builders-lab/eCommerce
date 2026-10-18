package com.ecommerce.platform.modules.checkout.controller;

import com.ecommerce.platform.modules.checkout.service.CheckoutService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/webhook")
public class PaymentWebhookController {

    private final CheckoutService checkoutService;

    public PaymentWebhookController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/{gateway}")
    public ResponseEntity<Void> handleWebhook(@PathVariable("gateway") String gateway,
                                              @RequestBody String payload,
                                              @RequestHeader HttpHeaders headers) {
        checkoutService.processPaymentWebhook(gateway, payload, resolveSignature(gateway, headers));
        return ResponseEntity.ok().build();
    }

    private String resolveSignature(String gateway, HttpHeaders headers) {
        String normalizedGateway = gateway == null ? "" : gateway.trim().toLowerCase();
        if ("stripe".equals(normalizedGateway)) {
            return headers.getFirst("Stripe-Signature");
        }
        if ("razorpay".equals(normalizedGateway)) {
            return headers.getFirst("X-Razorpay-Signature");
        }
        return headers.getFirst("X-Signature");
    }
}
