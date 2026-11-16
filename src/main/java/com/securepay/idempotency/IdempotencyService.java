package com.securepay.idempotency;

import com.securepay.domain.entity.IdempotencyRecord;
import com.securepay.domain.entity.Merchant;
import com.securepay.domain.enums.IdempotencyStatus;
import com.securepay.exception.IdempotencyConflictException;
import com.securepay.repository.IdempotencyRecordRepository;
import com.securepay.repository.MerchantRepository;
import com.securepay.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final MerchantRepository merchantRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository,
                              MerchantRepository merchantRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Checks if a request with the given idempotency key was previously processed.
     * @return IdempotencyRecord if matching cached response exists, or null if execution should proceed.
     * @throws IdempotencyConflictException if payload hash differs (conflicting replay) or currently in progress.
     */
    @Transactional
    public IdempotencyRecord checkOrStart(UUID merchantId, String idempotencyKey, String path, String method, String payload) {
        String payloadHash = HashUtils.sha256(payload != null ? payload.trim() : "");
        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                if (existing.getRequestPayloadHash().equals(payloadHash)) {
                    // Match! Return cached response
                    return existing;
                } else {
                    // Conflict! Same key used with different payload
                    throw new IdempotencyConflictException(
                            "Conflicting replay detected: Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload."
                    );
                }
            } else if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw new IdempotencyConflictException(
                        "Concurrent request in progress for Idempotency-Key '" + idempotencyKey + "'. Please wait or retry shortly."
                );
            }
        }

        // Create new in-progress record
        Merchant merchant = merchantRepository.getReferenceById(merchantId);
        IdempotencyRecord record = new IdempotencyRecord(merchant, idempotencyKey, path, method, payloadHash);
        return idempotencyRecordRepository.save(record);
    }

    @Transactional
    public void recordCompletion(UUID merchantId, String idempotencyKey, int httpStatus, String responseBody) {
        idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .ifPresent(record -> {
                    record.markCompleted(httpStatus, responseBody);
                    idempotencyRecordRepository.save(record);
                });
    }

    @Transactional
    public void recordFailure(UUID merchantId, String idempotencyKey, int httpStatus, String responseBody) {
        idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .ifPresent(record -> {
                    record.markFailed(httpStatus, responseBody);
                    idempotencyRecordRepository.save(record);
                });
    }
}
