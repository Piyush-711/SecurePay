package com.securepay.dto;

import java.time.Instant;
import java.util.UUID;

public record MerchantResponse(
    UUID id,
    String name,
    String email,
    boolean active,
    Instant createdAt
) {}
