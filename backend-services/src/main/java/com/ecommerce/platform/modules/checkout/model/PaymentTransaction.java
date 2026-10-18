package com.ecommerce.platform.modules.checkout.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_transactions_session", columnList = "provider_session_id"),
        @Index(name = "idx_payment_transactions_payment_intent", columnList = "payment_intent_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_transactions_idempotency", columnNames = "idempotency_key")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private CheckoutOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_webhook_event_id")
    private String lastWebhookEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CheckoutOrder getOrder() {
        return order;
    }

    public void setOrder(CheckoutOrder order) {
        this.order = order;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public void setProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getLastWebhookEventId() {
        return lastWebhookEventId;
    }

    public void setLastWebhookEventId(String lastWebhookEventId) {
        this.lastWebhookEventId = lastWebhookEventId;
    }
}
