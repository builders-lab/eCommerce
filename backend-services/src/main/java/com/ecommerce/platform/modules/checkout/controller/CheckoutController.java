package com.ecommerce.platform.modules.checkout.controller;

import com.ecommerce.platform.modules.checkout.dto.CheckoutResponse;
import com.ecommerce.platform.modules.checkout.dto.CheckoutStatusResponse;
import com.ecommerce.platform.modules.checkout.dto.SimulatePaymentRequest;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentSimulator;
import com.ecommerce.platform.modules.checkout.service.CheckoutService;
import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final LoadTestPaymentSimulator loadTestPaymentSimulator;

    public CheckoutController(CheckoutService checkoutService,
                              LoadTestPaymentSimulator loadTestPaymentSimulator) {
        this.checkoutService = checkoutService;
        this.loadTestPaymentSimulator = loadTestPaymentSimulator;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CheckoutResponse> checkout(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(checkoutService.createCheckoutSession(user));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CheckoutStatusResponse> getOrderStatus(@PathVariable("orderId") Long orderId,
                                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(checkoutService.getOrderStatus(orderId, user));
    }

    /**
     * Settles an order created by the local demo gateway by replaying a signed
     * payment event through the normal webhook path.
     *
     * <p>Returns 404 unless {@code APP_PAYMENT_GATEWAY=loadtest}, so it can never mark
     * an order created by a real payment provider as paid.</p>
     */
    @PostMapping("/{orderId}/simulate-payment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CheckoutStatusResponse> simulatePayment(
            @PathVariable("orderId") Long orderId,
            @RequestBody(required = false) SimulatePaymentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String outcome = request == null ? "SUCCEEDED" : request.outcome();
        return ResponseEntity.ok(loadTestPaymentSimulator.simulate(orderId, outcome, user));
    }
}
