package com.ecommerce.platform.modules.checkout.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_webhook_events_event_id", columnNames = "event_id")
})
public class WebhookEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public WebhookEventLog() {
    }

    public WebhookEventLog(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
