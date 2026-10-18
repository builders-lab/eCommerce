package com.ecommerce.platform.modules.checkout.payment.stripe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class StripeWebhookVerifier {

    private static final long DEFAULT_TOLERANCE_SECONDS = 300;

    private final StripeProperties stripeProperties;
    private final ObjectMapper objectMapper;

    public StripeWebhookVerifier(StripeProperties stripeProperties, ObjectMapper objectMapper) {
        this.stripeProperties = stripeProperties;
        this.objectMapper = objectMapper;
    }

    public StripeWebhookEvent verifyAndParse(String payload, String signatureHeader) {
        if (stripeProperties.webhookSecret() == null || stripeProperties.webhookSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Stripe webhook secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing Stripe-Signature header");
        }

        Map<String, String> parts = parseSignatureHeader(signatureHeader);
        String timestamp = parts.get("t");
        String signature = parts.get("v1");
        if (timestamp == null || signature == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Stripe-Signature header");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Stripe signature timestamp");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > DEFAULT_TOLERANCE_SECONDS) {
            throw new ResponseStatusException(BAD_REQUEST, "Stripe signature timestamp outside tolerance");
        }

        String signedPayload = timestamp + "." + payload;
        String expectedSignature = hmacSha256Hex(stripeProperties.webhookSecret(), signedPayload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Stripe webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.path("id").asString(null);
            String type = root.path("type").asString(null);
            JsonNode dataObject = root.path("data").path("object");
            if (eventId == null || type == null || dataObject.isMissingNode()) {
                throw new ResponseStatusException(BAD_REQUEST, "Malformed Stripe webhook payload");
            }
            return new StripeWebhookEvent(eventId, type, dataObject);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to parse Stripe webhook payload");
        }
    }

    private Map<String, String> parseSignatureHeader(String signatureHeader) {
        Map<String, String> parts = new HashMap<>();
        String[] tokens = signatureHeader.split(",");
        for (String token : tokens) {
            String[] kv = token.trim().split("=", 2);
            if (kv.length == 2) {
                parts.put(kv[0], kv[1]);
            }
        }
        return parts;
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
            throw new ResponseStatusException(BAD_REQUEST, "Unable to verify Stripe webhook signature");
        }
    }
}
