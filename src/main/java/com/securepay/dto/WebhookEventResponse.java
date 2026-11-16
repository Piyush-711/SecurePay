package com.securepay.dto;

import java.time.Instant;
import java.util.UUID;

public record WebhookEventResponse(
    UUID id,
    String eventId,
    String eventType,
    UUID paymentId,
    Long sequenceNumber,
    String status,
    String notes,
    Instant processedAt
) {}
