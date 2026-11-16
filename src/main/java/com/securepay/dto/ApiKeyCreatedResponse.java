package com.securepay.dto;

import com.securepay.domain.enums.ApiKeyStatus;
import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(
    UUID id,
    String name,
    String keyPrefix,
    String secretKey, // Returned ONLY once upon creation!
    ApiKeyStatus status,
    int rateLimitPerMinute,
    Instant createdAt,
    Instant expiresAt,
    String warning
) {
    public ApiKeyCreatedResponse(UUID id, String name, String keyPrefix, String secretKey, ApiKeyStatus status,
                                 int rateLimitPerMinute, Instant createdAt, Instant expiresAt) {
        this(id, name, keyPrefix, secretKey, status, rateLimitPerMinute, createdAt, expiresAt,
             "Save this secret key securely. It will never be displayed again.");
    }
}
