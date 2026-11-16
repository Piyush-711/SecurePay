package com.securepay.dto;

import com.securepay.domain.enums.ApiKeyStatus;
import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
    UUID id,
    String name,
    String keyPrefix,
    ApiKeyStatus status,
    int rateLimitPerMinute,
    Instant createdAt,
    Instant expiresAt,
    Instant revokedAt
) {}
