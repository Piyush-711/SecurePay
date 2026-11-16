package com.securepay.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CapturePaymentRequest(
    @DecimalMin(value = "0.01", message = "Capture amount must be greater than zero if provided")
    BigDecimal amount,

    String description
) {}
