package com.securepay.repository;

import com.securepay.domain.entity.ApiKey;
import com.securepay.domain.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);
    List<ApiKey> findByMerchantId(UUID merchantId);
    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);
}
