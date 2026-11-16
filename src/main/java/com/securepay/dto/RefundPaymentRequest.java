package com.securepay.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record RefundPaymentRequest(
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero if provided")
    BigDecimal amount,

    String reason
) {}
