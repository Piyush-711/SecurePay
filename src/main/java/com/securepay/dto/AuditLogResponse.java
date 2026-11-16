package com.securepay.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID merchantId,
    String actor,
    String action,
    String resourceType,
    String resourceId,
    String ipAddress,
    int httpStatus,
    long executionTimeMs,
    Instant createdAt
) {}
