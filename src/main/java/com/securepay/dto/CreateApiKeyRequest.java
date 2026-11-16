package com.securepay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
    @NotBlank(message = "API key description name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    String name,

    @Min(value = 1, message = "Rate limit must be at least 1 request/min")
    @Max(value = 10000, message = "Rate limit cannot exceed 10000 requests/min")
    Integer rateLimitPerMinute
) {}
