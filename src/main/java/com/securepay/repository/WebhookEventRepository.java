package com.securepay.repository;

import com.securepay.domain.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByEventId(String eventId);
    List<WebhookEvent> findByPaymentTransactionIdOrderBySequenceNumberAsc(UUID paymentTransactionId);
}
