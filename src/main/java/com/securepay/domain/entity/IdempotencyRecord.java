package com.securepay.domain.entity;

import com.securepay.domain.enums.IdempotencyStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_merchant_idempotency_key", columnNames = {"merchant_id", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String requestPath;

    @Column(nullable = false, length = 10)
    private String requestMethod;

    @Column(nullable = false, length = 64)
    private String requestPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

    private Integer responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public IdempotencyRecord() {}

    public IdempotencyRecord(Merchant merchant, String idempotencyKey, String requestPath, String requestMethod, String requestPayloadHash) {
        this.merchant = merchant;
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestMethod = requestMethod;
        this.requestPayloadHash = requestPayloadHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = Instant.now();
    }

    public void markCompleted(int responseStatus, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = Instant.now();
    }

    public void markFailed(int responseStatus, String responseBody) {
        this.status = IdempotencyStatus.FAILED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = Instant.now();
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPayloadHash() {
        return requestPayloadHash;
    }

    public void setRequestPayloadHash(String requestPayloadHash) {
        this.requestPayloadHash = requestPayloadHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyStatus status) {
        this.status = status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
