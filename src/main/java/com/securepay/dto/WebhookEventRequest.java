package com.securepay.dto;

import com.securepay.domain.enums.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record WebhookEventRequest(
    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotNull(message = "Event type is required")
    WebhookEventType eventType,

    @NotNull(message = "Payment ID is required")
    UUID paymentId,

    @NotNull(message = "Sequence number is required")
    Long sequenceNumber,

    BigDecimal amount,
    String failureReason
) {}
