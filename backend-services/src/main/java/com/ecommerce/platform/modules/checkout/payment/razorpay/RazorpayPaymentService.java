package com.ecommerce.platform.modules.checkout.payment.razorpay;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentResponse;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentService;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class RazorpayPaymentService implements PaymentService {

    private final RazorpayProperties razorpayProperties;
    private final ObjectMapper objectMapper;

    public RazorpayPaymentService(RazorpayProperties razorpayProperties, ObjectMapper objectMapper) {
        this.razorpayProperties = razorpayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        if (razorpayProperties.keyId() == null || razorpayProperties.keyId().isBlank()
                || razorpayProperties.keySecret() == null || razorpayProperties.keySecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Razorpay is not configured");
        }

        String providerOrderId = "rzp_order_" + UUID.randomUUID();
        String checkoutUrl = razorpayProperties.checkoutBaseUrl()
                + "?orderRef=" + urlEncode(providerOrderId)
                + "&orderId=" + request.orderId()
                + "&keyId=" + urlEncode(razorpayProperties.keyId());

        return new PaymentResponse(
                PaymentProvider.RAZORPAY,
                PaymentStatus.PENDING,
                checkoutUrl,
                providerOrderId,
                null,
                null,
                "Razorpay order created"
        );
    }

    @Override
    public PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request) {
        if (razorpayProperties.webhookSecret() == null || razorpayProperties.webhookSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Razorpay webhook secret is not configured");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing X-Razorpay-Signature header");
        }

        String expectedSignature = hmacSha256Hex(razorpayProperties.webhookSecret(), request.payload());
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                request.signature().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Razorpay webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(request.payload());
            String eventId = root.path("payload").path("payment").path("entity").path("id").asString(null);
            if (eventId == null || eventId.isBlank()) {
                eventId = "rzp_event_" + UUID.randomUUID();
            }
            String eventType = root.path("event").asString("");
            String orderId = blankToNull(root.path("payload").path("payment").path("entity").path("order_id").asString(null));
            String paymentId = blankToNull(root.path("payload").path("payment").path("entity").path("id").asString(null));

            if ("payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
                return new PaymentVerifyResponse(
                        PaymentProvider.RAZORPAY,
                        eventId,
                        eventType,
                        true,
                        orderId,
                        paymentId,
                        PaymentStatus.SUCCEEDED,
                        "Razorpay payment captured"
                );
            }

            if ("payment.failed".equals(eventType)) {
                return new PaymentVerifyResponse(
                        PaymentProvider.RAZORPAY,
                        eventId,
                        eventType,
                        true,
                        orderId,
                        paymentId,
                        PaymentStatus.FAILED,
                        "Razorpay payment failed"
                );
            }

            return new PaymentVerifyResponse(
                    PaymentProvider.RAZORPAY,
                    eventId,
                    eventType,
                    false,
                    orderId,
                    paymentId,
                    PaymentStatus.PENDING,
                    "Unhandled Razorpay event type"
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to parse Razorpay webhook payload");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
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
            throw new ResponseStatusException(BAD_REQUEST, "Unable to verify Razorpay webhook signature");
        }
    }
}
