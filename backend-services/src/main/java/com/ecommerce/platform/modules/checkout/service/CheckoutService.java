package com.ecommerce.platform.modules.checkout.service;

import com.ecommerce.platform.modules.cart.dto.CartItemDto;
import com.ecommerce.platform.modules.cart.dto.CartResponse;
import com.ecommerce.platform.modules.cart.service.CartService;
import com.ecommerce.platform.modules.checkout.dto.CheckoutResponse;
import com.ecommerce.platform.modules.checkout.dto.CheckoutStatusResponse;
import com.ecommerce.platform.modules.checkout.model.*;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentLineItem;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentResponse;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentService;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentServiceResolver;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyRequest;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyResponse;
import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import com.ecommerce.platform.modules.checkout.repository.CheckoutOrderRepository;
import com.ecommerce.platform.modules.checkout.repository.PaymentTransactionRepository;
import com.ecommerce.platform.modules.checkout.repository.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartService cartService;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final PaymentServiceResolver paymentServiceResolver;
    private final String defaultCurrency;

    public CheckoutService(CartService cartService,
                           CheckoutOrderRepository checkoutOrderRepository,
                           PaymentTransactionRepository paymentTransactionRepository,
                           WebhookEventLogRepository webhookEventLogRepository,
                           PaymentServiceResolver paymentServiceResolver,
                           @Value("${app.payment.default-currency:usd}") String defaultCurrency) {
        this.cartService = cartService;
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.paymentServiceResolver = paymentServiceResolver;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public CheckoutResponse createCheckoutSession(AuthenticatedUser user) {
        CartResponse cart = cartService.getCart(user);
        if (cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        CheckoutOrder order = buildOrderSnapshot(user, cart);
        CheckoutOrder savedOrder = Objects.requireNonNull(
                checkoutOrderRepository.save(order),
                "Saved order must not be null"
        );

        PaymentService paymentService = paymentServiceResolver.resolveConfigured();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(savedOrder);
        transaction.setProvider(paymentService.getProvider());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setIdempotencyKey(UUID.randomUUID().toString());
        paymentTransactionRepository.save(transaction);

        savedOrder.setPaymentTransaction(transaction);

        PaymentRequest paymentRequest = new PaymentRequest(
                savedOrder.getId(),
                savedOrder.getCurrency(),
                cart.items().stream()
                        .map(item -> new PaymentLineItem(item.title(), item.finalPrice(), item.quantity()))
                        .toList(),
                transaction.getIdempotencyKey()
        );

        try {
            PaymentResponse response = paymentService.createPayment(paymentRequest);
            if (response.provider() != null) {
                transaction.setProvider(response.provider());
            }
            if (response.status() != null) {
                transaction.setStatus(response.status());
            }
            transaction.setProviderSessionId(blankToNull(response.providerReferenceId()));
            transaction.setPaymentIntentId(blankToNull(response.paymentReferenceId()));
            transaction.setExpiresAt(response.expiresAt());
            paymentTransactionRepository.save(transaction);

            return new CheckoutResponse(
                    savedOrder.getId(),
                    savedOrder.getStatus().name(),
                    response.checkoutUrl(),
                    response.expiresAt() != null ? response.expiresAt().toString() : null
            );
        } catch (ResponseStatusException ex) {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason(ex.getReason());
            paymentTransactionRepository.save(transaction);
            savedOrder.setStatus(OrderStatus.FAILED);
            checkoutOrderRepository.save(savedOrder);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CheckoutStatusResponse getOrderStatus(Long orderId, AuthenticatedUser requester) {
        CheckoutOrder order = checkoutOrderRepository.findDetailedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        boolean isOwner = order.getUserId().equals(requester.getId());
        boolean isAdmin = requester.hasRole("ROLE_ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this order");
        }

        PaymentStatus paymentStatus = Optional.ofNullable(order.getPaymentTransaction())
                .map(PaymentTransaction::getStatus)
                .orElse(PaymentStatus.PENDING);

        return new CheckoutStatusResponse(
                order.getId(),
                order.getStatus().name(),
                paymentStatus.name(),
                statusMessage(order.getStatus(), paymentStatus)
        );
    }

    @Transactional
    public void processPaymentWebhook(String gateway, String payload, String signatureHeader) {
        PaymentService paymentService = paymentServiceResolver.resolveByGateway(gateway);
        PaymentVerifyResponse event = paymentService.verifyPayment(new PaymentVerifyRequest(payload, signatureHeader));

        if (!event.supportedEvent()) {
            log.info("Ignoring unhandled webhook type={} provider={}", event.eventType(), paymentService.getProvider());
            return;
        }

        String eventId = paymentService.getProvider().name() + ":" + event.eventId();
        try {
            webhookEventLogRepository.saveAndFlush(new WebhookEventLog(eventId, event.eventType()));
            applyWebhookTransition(paymentService.getProvider(), eventId, event);
        } catch (DataIntegrityViolationException ex) {
            log.info("Ignoring duplicate webhook race eventId={}", eventId);
        }
    }

    private void applyWebhookTransition(PaymentProvider provider, String eventId, PaymentVerifyResponse event) {
        PaymentTransaction transaction = findTransaction(provider, event)
                .orElse(null);

        if (transaction == null) {
            log.warn("Webhook event points to unknown transaction provider={} ref={} paymentRef={}",
                    provider, event.providerReferenceId(), event.paymentReferenceId());
            return;
        }

        if (transaction.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        if (event.providerReferenceId() != null && !event.providerReferenceId().isBlank()) {
            transaction.setProviderSessionId(event.providerReferenceId());
        }
        if (event.paymentReferenceId() != null && !event.paymentReferenceId().isBlank()) {
            transaction.setPaymentIntentId(event.paymentReferenceId());
        }
        transaction.setLastWebhookEventId(eventId);

        CheckoutOrder order = transaction.getOrder();
        PaymentStatus nextStatus = event.paymentStatus();

        if (nextStatus == PaymentStatus.SUCCEEDED) {
            transaction.setStatus(PaymentStatus.SUCCEEDED);
            paymentTransactionRepository.save(transaction);
            order.setStatus(OrderStatus.PAID);
            checkoutOrderRepository.save(order);
            cartService.clearByUserId(
                    order.getUserId()
            );
            return;
        }

        if (nextStatus == PaymentStatus.EXPIRED) {
            transaction.setStatus(PaymentStatus.EXPIRED);
            paymentTransactionRepository.save(transaction);
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.EXPIRED);
                checkoutOrderRepository.save(order);
            }
            return;
        }

        if (nextStatus == PaymentStatus.FAILED) {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason(blankToNull(event.message()));
            paymentTransactionRepository.save(transaction);
            if (order.getStatus() != OrderStatus.PAID) {
                order.setStatus(OrderStatus.FAILED);
                checkoutOrderRepository.save(order);
            }
        }
    }

    private Optional<PaymentTransaction> findTransaction(PaymentProvider provider, PaymentVerifyResponse event) {
        String providerReference = blankToNull(event.providerReferenceId());
        if (providerReference != null) {
            Optional<PaymentTransaction> byProviderReference =
                    paymentTransactionRepository.findByProviderAndProviderSessionId(provider, providerReference);
            if (byProviderReference.isPresent()) {
                return byProviderReference;
            }
        }

        String paymentReference = blankToNull(event.paymentReferenceId());
        if (paymentReference != null) {
            return paymentTransactionRepository.findByProviderAndPaymentIntentId(provider, paymentReference);
        }

        return Optional.empty();
    }

    private CheckoutOrder buildOrderSnapshot(AuthenticatedUser user, CartResponse cart) {
        CheckoutOrder order = new CheckoutOrder();
        order.setUserId(user.getId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency(resolveCurrency());
        order.setTotalAmount(BigDecimal.ZERO);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemDto item : cart.items()) {
            CheckoutOrderItem snapshot = new CheckoutOrderItem();
            snapshot.setProductId(item.id());
            snapshot.setTitle(item.title());
            snapshot.setUnitPrice(item.price());
            snapshot.setFinalUnitPrice(item.finalPrice());
            snapshot.setQuantity(item.quantity());
            snapshot.setProductDiscountPercentage(item.productDiscount() != null ? item.productDiscount().percentage() : BigDecimal.ZERO);
            snapshot.setUserDiscountPercentage(defaultDecimal(item.userDiscountPercentage()));
            snapshot.setEmployeeDiscountPercentage(defaultDecimal(item.employeeDiscountPercentage()));
            snapshot.setTotalDiscountPercentage(defaultDecimal(item.totalDiscountPercentage()));
            order.addItem(snapshot);

            totalAmount = totalAmount.add(item.finalPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalAmount(totalAmount);
        return order;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String statusMessage(OrderStatus orderStatus, PaymentStatus paymentStatus) {
        if (orderStatus == OrderStatus.PAID && paymentStatus == PaymentStatus.SUCCEEDED) {
            return "Payment completed";
        }
        if (orderStatus == OrderStatus.EXPIRED) {
            return "Checkout session expired";
        }
        if (orderStatus == OrderStatus.FAILED) {
            return "Payment failed";
        }
        return "Awaiting payment";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String resolveCurrency() {
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            return "usd";
        }
        return defaultCurrency.trim().toLowerCase();
    }
}
