package com.ecommerce.platform.checkout;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentLineItem;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyRequest;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentProperties;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoadTestPaymentServiceTest {

    private static final String SECRET = "loadtest-secret";

    private final LoadTestPaymentService service = new LoadTestPaymentService(
            new LoadTestPaymentProperties(0, 0, SECRET, "http://localhost/loadtest"),
            new ObjectMapper()
    );

    @Test
    void create_payment_returns_fake_session_references() {
        var response = service.createPayment(new PaymentRequest(
                42L,
                "usd",
                List.of(new PaymentLineItem("Item", BigDecimal.TEN, 1)),
                "idempotency-key"
        ));

        assertEquals(PaymentProvider.LOADTEST, response.provider());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertTrue(response.checkoutUrl().contains("sessionId=lt_session_42_"));
        assertTrue(response.providerReferenceId().startsWith("lt_session_42_"));
        assertTrue(response.paymentReferenceId().startsWith("lt_payment_42_"));
    }

    @Test
    void verify_payment_accepts_signed_success_payload() {
        String payload = """
                {"eventId":"evt_1","eventType":"checkout.session.completed","providerReferenceId":"lt_session_1","paymentReferenceId":"lt_payment_1"}
                """;

        var response = service.verifyPayment(new PaymentVerifyRequest(payload, hmacSha256Hex(SECRET, payload)));

        assertEquals(PaymentProvider.LOADTEST, response.provider());
        assertEquals("evt_1", response.eventId());
        assertTrue(response.supportedEvent());
        assertEquals(PaymentStatus.SUCCEEDED, response.paymentStatus());
    }

    @Test
    void verify_payment_rejects_bad_signature() {
        String payload = "{\"eventId\":\"evt_1\"}";

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.verifyPayment(new PaymentVerifyRequest(payload, "bad"))
        );

        assertEquals(400, ex.getStatusCode().value());
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
