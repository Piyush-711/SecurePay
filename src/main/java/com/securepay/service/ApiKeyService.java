package com.securepay.service;

import com.securepay.domain.entity.ApiKey;
import com.securepay.domain.entity.Merchant;
import com.securepay.domain.enums.ApiKeyStatus;
import com.securepay.dto.ApiKeyCreatedResponse;
import com.securepay.dto.ApiKeyResponse;
import com.securepay.dto.CreateApiKeyRequest;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.ApiKeyRepository;
import com.securepay.repository.MerchantRepository;
import com.securepay.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         MerchantRepository merchantRepository,
                         AuditLogService auditLogService) {
        this.apiKeyRepository = apiKeyRepository;
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ApiKeyCreatedResponse createApiKey(UUID merchantId, CreateApiKeyRequest request, String actor, String ipAddress) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        String rawSecret = HashUtils.generateSecureApiKey("sec_live");
        String prefix = rawSecret.substring(0, Math.min(16, rawSecret.length()));
        String keyHash = HashUtils.sha256(rawSecret);
        int rateLimit = request.rateLimitPerMinute() != null ? request.rateLimitPerMinute() : 60;

        ApiKey apiKey = new ApiKey(merchant, request.name(), prefix, keyHash, rateLimit, null);
        apiKey = apiKeyRepository.save(apiKey);

        auditLogService.record(
                merchantId,
                actor,
                "API_KEY_CREATE",
                "API_KEY",
                apiKey.getId().toString(),
                ipAddress,
                201,
                0
        );

        return new ApiKeyCreatedResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawSecret,
                apiKey.getStatus(),
                apiKey.getRateLimitPerMinute(),
                apiKey.getCreatedAt(),
                apiKey.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID merchantId) {
        return apiKeyRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(UUID merchantId, UUID keyId, String actor, String ipAddress) {
        ApiKey apiKey = apiKeyRepository.findByIdAndMerchantId(keyId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + keyId));

        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());
        apiKey = apiKeyRepository.save(apiKey);

        auditLogService.record(
                merchantId,
                actor,
                "API_KEY_REVOKE",
                "API_KEY",
                apiKey.getId().toString(),
                ipAddress,
                200,
                0
        );

        return mapToResponse(apiKey);
    }

    private ApiKeyResponse mapToResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getRateLimitPerMinute(),
                apiKey.getCreatedAt(),
                apiKey.getExpiresAt(),
                apiKey.getRevokedAt()
        );
    }
}
