package com.ecommerce.platform.modules.checkout.payment.loadtest;

import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentResponse;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentService;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class LoadTestPaymentService implements PaymentService {

    private final LoadTestPaymentProperties properties;
    private final ObjectMapper objectMapper;

    public LoadTestPaymentService(LoadTestPaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.LOADTEST;
    }

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        applyConfiguredDelay();
        maybeFailPaymentCreation();

        String sessionId = "lt_session_" + request.orderId() + "_" + UUID.randomUUID();
        String paymentId = "lt_payment_" + request.orderId() + "_" + UUID.randomUUID();
        String checkoutUrl = properties.checkoutBaseUrl()
                + "?sessionId=" + urlEncode(sessionId)
                + "&paymentId=" + urlEncode(paymentId)
                + "&orderId=" + request.orderId();

        return new PaymentResponse(
                PaymentProvider.LOADTEST,
                PaymentStatus.PENDING,
                checkoutUrl,
                sessionId,
                paymentId,
                Instant.now().plusSeconds(1800),
                "Load-test checkout session created"
        );
    }

    @Override
    public PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Load-test webhook secret is not configured");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing X-Signature header");
        }

        String expectedSignature = LoadTestWebhookSignature.hmacSha256Hex(properties.webhookSecret(), request.payload());
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                request.signature().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid load-test webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(request.payload());
            String eventId = textOrDefault(root, "eventId", "lt_evt_" + UUID.randomUUID());
            String eventType = textOrDefault(root, "eventType", "checkout.session.completed");
            String sessionId = blankToNull(root.path("providerReferenceId").asString(null));
            String paymentId = blankToNull(root.path("paymentReferenceId").asString(null));
            PaymentStatus status = resolveStatus(eventType, root.path("status").asString(null));

            return new PaymentVerifyResponse(
                    PaymentProvider.LOADTEST,
                    eventId,
                    eventType,
                    isSupported(status),
                    sessionId,
                    paymentId,
                    status,
                    textOrDefault(root, "message", "Load-test webhook processed")
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to parse load-test webhook payload");
        }
    }

    private void applyConfiguredDelay() {
        if (properties.delayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.delayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Load-test payment interrupted");
        }
    }

    private void maybeFailPaymentCreation() {
        double failureRate = Math.clamp(properties.failureRate(), 0, 1);
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new ResponseStatusException(BAD_GATEWAY, "Load-test payment failure");
        }
    }

    private PaymentStatus resolveStatus(String eventType, String explicitStatus) {
        String normalizedStatus = explicitStatus == null ? "" : explicitStatus.trim().toUpperCase();
        if (!normalizedStatus.isBlank()) {
            return PaymentStatus.valueOf(normalizedStatus);
        }
        return switch (eventType) {
            case "checkout.session.completed", "payment.succeeded", "loadtest.payment.succeeded" -> PaymentStatus.SUCCEEDED;
            case "checkout.session.expired", "payment.expired", "loadtest.payment.expired" -> PaymentStatus.EXPIRED;
            case "payment.failed", "loadtest.payment.failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private boolean isSupported(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.EXPIRED || status == PaymentStatus.FAILED;
    }

    private String textOrDefault(JsonNode root, String fieldName, String fallback) {
        String value = root.path(fieldName).asString(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
