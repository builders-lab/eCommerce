package com.ecommerce.platform.modules.checkout.dto;

/**
 * Outcome requested when replaying a payment event through the local demo gateway.
 *
 * @param outcome one of {@code SUCCEEDED}, {@code FAILED}, or {@code EXPIRED}
 */
public record SimulatePaymentRequest(String outcome) {
}