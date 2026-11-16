package com.securepay.controller;

import com.securepay.dto.ApiKeyCreatedResponse;
import com.securepay.dto.ApiKeyResponse;
import com.securepay.dto.CreateApiKeyRequest;
import com.securepay.security.MerchantPrincipal;
import com.securepay.security.SecurityUtils;
import com.securepay.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@PreAuthorize("hasRole('MERCHANT_ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request,
                                                              HttpServletRequest httpRequest) {
        MerchantPrincipal principal = SecurityUtils.getCurrentPrincipal();
        String ip = getClientIp(httpRequest);
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(
                principal.getMerchantId(),
                request,
                principal.getIdentifier(),
                ip
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        UUID merchantId = SecurityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(apiKeyService.listApiKeys(merchantId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> revokeApiKey(@PathVariable("id") UUID id,
                                                       HttpServletRequest httpRequest) {
        MerchantPrincipal principal = SecurityUtils.getCurrentPrincipal();
        String ip = getClientIp(httpRequest);
        ApiKeyResponse response = apiKeyService.revokeApiKey(
                principal.getMerchantId(),
                id,
                principal.getIdentifier(),
                ip
        );
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0] : request.getRemoteAddr();
    }
}
