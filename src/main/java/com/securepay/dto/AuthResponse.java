package com.securepay.dto;

import java.util.UUID;

public record AuthResponse(
    String token,
    String tokenType,
    UUID merchantId,
    String username,
    String role,
    long expiresInMs
) {
    public AuthResponse(String token, UUID merchantId, String username, String role, long expiresInMs) {
        this(token, "Bearer", merchantId, username, role, expiresInMs);
    }
}
