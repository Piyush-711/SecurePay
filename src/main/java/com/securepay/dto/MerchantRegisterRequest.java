package com.securepay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRegisterRequest(
    @NotBlank(message = "Merchant business name is required")
    @Size(min = 2, max = 100, message = "Business name must be between 2 and 100 characters")
    String businessName,

    @NotBlank(message = "Admin email is required")
    @Email(message = "Valid email is required")
    String email,

    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    String password
) {}
