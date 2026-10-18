package com.ecommerce.platform.modules.checkout.payment.loadtest;

import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import com.ecommerce.platform.modules.checkout.dto.CheckoutStatusResponse;
import com.ecommerce.platform.modules.checkout.model.CheckoutOrder;
import com.ecommerce.platform.modules.checkout.model.OrderStatus;
import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;
import com.ecommerce.platform.modules.checkout.model.PaymentTransaction;
import com.ecommerce.platform.modules.checkout.repository.CheckoutOrderRepository;
import com.ecommerce.platform.modules.checkout.service.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.UUID;

/**
 * Turns a pending local-gateway order into a settled one from the browser.
 *
 * <p>This deliberately does <em>not</em> mutate order state directly. It builds the
 * same webhook event a real provider would send, signs it with the shared local
 * secret, and replays it through {@link CheckoutService#processPaymentWebhook}.
 * Signature verification, duplicate-event protection, the order transition, and
 * cart clearing therefore all run exactly as they would for a genuine callback.</p>
 *
 * <p>Only active while {@code app.payment.gateway=loadtest}, so it can never mark an
 * order created by a real payment provider as paid.</p>
 */
@Service
public class LoadTestPaymentSimulator {

    private static final Logger log = LoggerFactory.getLogger(LoadTestPaymentSimulator.class);

    private static final Set<String> SUPPORTED_OUTCOMES = Set.of("SUCCEEDED", "FAILED", "EXPIRED");

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final CheckoutService checkoutService;
    private final LoadTestPaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final String configuredGateway;

    public LoadTestPaymentSimulator(CheckoutOrderRepository checkoutOrderRepository,
                                    CheckoutService checkoutService,
                                    LoadTestPaymentProperties properties,
                                    ObjectMapper objectMapper,
                                    @Value("${app.payment.gateway:stripe}") String configuredGateway) {
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.checkoutService = checkoutService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.configuredGateway = configuredGateway;
    }

    /**
     * Replays a signed payment event for the given order and returns its refreshed status.
     *
     * <p>Intentionally not {@code @Transactional}: the read must not wrap the write
     * performed by {@link CheckoutService#processPaymentWebhook} in a read-only
     * transaction. Each collaborator manages its own transaction.</p>
     */
    public CheckoutStatusResponse simulate(Long orderId, String outcome, AuthenticatedUser requester) {
        String normalizedOutcome = normalizeOutcome(outcome);
        requireLocalGatewayConfigured();

        CheckoutOrder order = checkoutOrderRepository.findDetailedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        boolean isOwner = order.getUserId().equals(requester.getId());
        if (!isOwner && !requester.hasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to pay for this order");
        }

        PaymentTransaction transaction = order.getPaymentTransaction();
        if (transaction == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order has no payment transaction");
        }
        if (transaction.getProvider() != PaymentProvider.LOADTEST) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order was not created by the local demo gateway");
        }
        if (order.getStatus() == OrderStatus.PAID || transaction.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already paid");
        }

        String payload = buildEventPayload(transaction, normalizedOutcome);
        String signature = LoadTestWebhookSignature.hmacSha256Hex(properties.webhookSecret(), payload);

        log.info("Replaying simulated load-test payment event orderId={} outcome={}", orderId, normalizedOutcome);
        checkoutService.processPaymentWebhook(PaymentProvider.LOADTEST.getConfigKey(), payload, signature);

        return checkoutService.getOrderStatus(orderId, requester);
    }

    private String normalizeOutcome(String outcome) {
        String normalized = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!SUPPORTED_OUTCOMES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported outcome: " + outcome + ". Use SUCCEEDED, FAILED, or EXPIRED.");
        }
        return normalized;
    }

    private void requireLocalGatewayConfigured() {
        PaymentProvider provider;
        try {
            provider = PaymentProvider.fromConfigValue(configuredGateway);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Local payment simulation is unavailable");
        }
        if (provider != PaymentProvider.LOADTEST) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Local payment simulation is only available when APP_PAYMENT_GATEWAY=loadtest");
        }
    }

    private String buildEventPayload(PaymentTransaction transaction, String outcome) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", "lt_evt_" + UUID.randomUUID());
        root.put("eventType", "loadtest.payment." + outcome.toLowerCase());
        if (transaction.getProviderSessionId() != null) {
            root.put("providerReferenceId", transaction.getProviderSessionId());
        }
        if (transaction.getPaymentIntentId() != null) {
            root.put("paymentReferenceId", transaction.getPaymentIntentId());
        }
        root.put("status", outcome);
        root.put("message", messageFor(outcome));
        return root.toString();
    }

    private String messageFor(String outcome) {
        return switch (outcome) {
            case "SUCCEEDED" -> "Simulated payment approved";
            case "FAILED" -> "Simulated payment declined";
            default -> "Simulated session expired";
        };
    }
}