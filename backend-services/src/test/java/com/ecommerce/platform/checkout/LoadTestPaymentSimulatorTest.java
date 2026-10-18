package com.ecommerce.platform.checkout;

import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import com.ecommerce.platform.modules.checkout.dto.CheckoutStatusResponse;
import com.ecommerce.platform.modules.checkout.model.CheckoutOrder;
import com.ecommerce.platform.modules.checkout.model.OrderStatus;
import com.ecommerce.platform.modules.checkout.model.PaymentProvider;
import com.ecommerce.platform.modules.checkout.model.PaymentStatus;
import com.ecommerce.platform.modules.checkout.model.PaymentTransaction;
import com.ecommerce.platform.modules.checkout.payment.core.PaymentVerifyRequest;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentProperties;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentService;
import com.ecommerce.platform.modules.checkout.payment.loadtest.LoadTestPaymentSimulator;
import com.ecommerce.platform.modules.checkout.repository.CheckoutOrderRepository;
import com.ecommerce.platform.modules.checkout.service.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoadTestPaymentSimulatorTest {

    private static final String SECRET = "loadtest-secret";
    private static final Long ORDER_ID = 7L;
    private static final Long OWNER_ID = 42L;

    private CheckoutOrderRepository orderRepository;
    private CheckoutService checkoutService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository = mock(CheckoutOrderRepository.class);
        checkoutService = mock(CheckoutService.class);
        objectMapper = new ObjectMapper();
    }

    private LoadTestPaymentSimulator simulator(String gateway) {
        return new LoadTestPaymentSimulator(
                orderRepository,
                checkoutService,
                new LoadTestPaymentProperties(0, 0, SECRET, "http://localhost/checkout/loadtest"),
                objectMapper,
                gateway
        );
    }

    private CheckoutOrder pendingLoadTestOrder() {
        CheckoutOrder order = new CheckoutOrder();
        order.setUserId(OWNER_ID);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setProvider(PaymentProvider.LOADTEST);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setProviderSessionId("lt_session_7_abc");
        transaction.setPaymentIntentId("lt_payment_7_def");

        order.setPaymentTransaction(transaction);
        return order;
    }

    private AuthenticatedUser user(Long id, String... roles) {
        return new AuthenticatedUser(id, "user@example.com", "User",
                List.of(roles), null, null, null, true, true);
    }

    @Test
    void emits_event_that_the_real_webhook_verifier_accepts() {
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(pendingLoadTestOrder()));
        when(checkoutService.getOrderStatus(eq(ORDER_ID), any()))
                .thenReturn(new CheckoutStatusResponse(ORDER_ID, "PAID", "SUCCEEDED", "Payment completed"));

        CheckoutStatusResponse response = simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(OWNER_ID));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signature = ArgumentCaptor.forClass(String.class);
        verify(checkoutService).processPaymentWebhook(eq("loadtest"), payload.capture(), signature.capture());

        // Replay through the production verifier to prove the signature and shape are valid.
        LoadTestPaymentService verifier = new LoadTestPaymentService(
                new LoadTestPaymentProperties(0, 0, SECRET, "http://localhost/checkout/loadtest"),
                objectMapper
        );

        var event = verifier.verifyPayment(new PaymentVerifyRequest(payload.getValue(), signature.getValue()));

        assertTrue(event.supportedEvent());
        assertEquals(PaymentStatus.SUCCEEDED, event.paymentStatus());
        assertEquals("lt_session_7_abc", event.providerReferenceId());
        assertEquals("lt_payment_7_def", event.paymentReferenceId());
        assertEquals("PAID", response.status());
    }

    @Test
    void decline_emits_a_failed_event() {
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(pendingLoadTestOrder()));
        when(checkoutService.getOrderStatus(eq(ORDER_ID), any()))
                .thenReturn(new CheckoutStatusResponse(ORDER_ID, "FAILED", "FAILED", "Simulated payment declined"));

        simulator("loadtest").simulate(ORDER_ID, "FAILED", user(OWNER_ID));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(checkoutService).processPaymentWebhook(eq("loadtest"), payload.capture(), anyString());
        assertTrue(payload.getValue().contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejects_unknown_outcome() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("loadtest").simulate(ORDER_ID, "REFUNDED", user(OWNER_ID)));

        assertEquals(400, ex.getStatusCode().value());
        verify(checkoutService, never()).processPaymentWebhook(anyString(), anyString(), anyString());
    }

    @Test
    void rejects_caller_who_does_not_own_the_order() {
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(pendingLoadTestOrder()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(999L)));

        assertEquals(403, ex.getStatusCode().value());
        verify(checkoutService, never()).processPaymentWebhook(anyString(), anyString(), anyString());
    }

    @Test
    void allows_admin_to_settle_another_users_order() {
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(pendingLoadTestOrder()));
        when(checkoutService.getOrderStatus(eq(ORDER_ID), any()))
                .thenReturn(new CheckoutStatusResponse(ORDER_ID, "PAID", "SUCCEEDED", "Payment completed"));

        simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(999L, "ROLE_ADMIN"));

        verify(checkoutService).processPaymentWebhook(eq("loadtest"), anyString(), anyString());
    }

    @Test
    void rejects_order_already_paid() {
        CheckoutOrder order = pendingLoadTestOrder();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(OWNER_ID)));

        assertEquals(409, ex.getStatusCode().value());
        verify(checkoutService, never()).processPaymentWebhook(anyString(), anyString(), anyString());
    }

    @Test
    void rejects_order_created_by_a_real_provider() {
        CheckoutOrder order = pendingLoadTestOrder();
        order.getPaymentTransaction().setProvider(PaymentProvider.STRIPE);
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(OWNER_ID)));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void unavailable_when_a_real_gateway_is_configured() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("stripe").simulate(ORDER_ID, "SUCCEEDED", user(OWNER_ID)));

        assertEquals(404, ex.getStatusCode().value());
        verify(orderRepository, never()).findDetailedById(anyLong());
        verify(checkoutService, never()).processPaymentWebhook(anyString(), anyString(), anyString());
    }

    @Test
    void missing_order_returns_not_found() {
        when(orderRepository.findDetailedById(ORDER_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> simulator("loadtest").simulate(ORDER_ID, "SUCCEEDED", user(OWNER_ID)));

        assertEquals(404, ex.getStatusCode().value());
        assertNotNull(ex.getReason());
    }
}