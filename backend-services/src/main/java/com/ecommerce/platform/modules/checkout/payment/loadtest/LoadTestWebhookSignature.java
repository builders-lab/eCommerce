package com.ecommerce.platform.modules.checkout.payment.loadtest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Shared HMAC helper for the local payment gateway.
 *
 * <p>Used both by {@link LoadTestPaymentService} when verifying inbound webhook
 * signatures and by {@link LoadTestPaymentSimulator} when signing the events it
 * replays through the real webhook path. Keeping the algorithm in one place stops
 * the signer and verifier from drifting apart.</p>
 */
final class LoadTestWebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private LoadTestWebhookSignature() {
    }

    static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute load-test webhook signature", ex);
        }
    }
}