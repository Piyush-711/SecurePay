package com.securepay.domain.entity;

import com.securepay.domain.enums.ApiKeyStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_api_key_hash", columnList = "keyHash", unique = true)
})
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String keyPrefix;

    @Column(nullable = false, unique = true, length = 128)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(nullable = false)
    private int rateLimitPerMinute = 60;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant expiresAt;

    private Instant revokedAt;

    public ApiKey() {}

    public ApiKey(Merchant merchant, String name, String keyPrefix, String keyHash, int rateLimitPerMinute, Instant expiresAt) {
        this.merchant = merchant;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.expiresAt = expiresAt;
        this.status = ApiKeyStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public void setStatus(ApiKeyStatus status) {
        this.status = status;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
