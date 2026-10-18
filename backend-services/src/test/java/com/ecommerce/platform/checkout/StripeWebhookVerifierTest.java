package com.ecommerce.platform.checkout;

import tools.jackson.databind.ObjectMapper;
import com.ecommerce.platform.modules.checkout.payment.stripe.StripeProperties;
import com.ecommerce.platform.modules.checkout.payment.stripe.StripeWebhookEvent;
import com.ecommerce.platform.modules.checkout.payment.stripe.StripeWebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeWebhookVerifierTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private StripeWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties(
                "",
                "",
                WEBHOOK_SECRET,
                "https://api.stripe.com/v1",
                "http://localhost:5173/checkout/success",
                "http://localhost:5173/checkout/cancel",
                "usd",
                3000,
                10000,
                2
        );
        verifier = new StripeWebhookVerifier(properties, new ObjectMapper());
    }

    @Test
    void verify_and_parse_valid_signature() {
        String payload = """
                {
                  "id": "evt_123",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "payment_intent": "pi_test_123"
                    }
                  }
                }
                """;
        String header = buildSignatureHeader(payload, Instant.now().getEpochSecond());

        StripeWebhookEvent event = verifier.verifyAndParse(payload, header);

        assertEquals("evt_123", event.eventId());
        assertEquals("checkout.session.completed", event.type());
        assertEquals("cs_test_123", event.dataObject().path("id").asString());
    }

    @Test
    void reject_missing_signature_header() {
        String payload = """
                {"id":"evt_123","type":"checkout.session.completed","data":{"object":{"id":"cs_test_123"}}}
                """;
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyAndParse(payload, null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reject_invalid_signature() {
        String payload = """
                {"id":"evt_123","type":"checkout.session.completed","data":{"object":{"id":"cs_test_123"}}}
                """;
        String badHeader = "t=" + Instant.now().getEpochSecond() + ",v1=bad_signature";

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyAndParse(payload, badHeader));
        assertEquals(400, ex.getStatusCode().value());
    }

    private String buildSignatureHeader(String payload, long timestamp) {
        String signedPayload = timestamp + "." + payload;
        String signature = hmacSha256Hex(WEBHOOK_SECRET, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
