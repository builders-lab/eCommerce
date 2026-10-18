package com.ecommerce.platform.modules.checkout.repository;

import com.ecommerce.platform.modules.checkout.model.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {
    boolean existsByEventId(String eventId);
}
