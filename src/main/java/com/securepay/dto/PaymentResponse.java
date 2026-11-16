package com.securepay.dto;

import com.securepay.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID merchantId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String customerEmail,
    String description,
    BigDecimal capturedAmount,
    BigDecimal refundedAmount,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {}
