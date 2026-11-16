package com.securepay.service;

import com.securepay.domain.entity.AuditLog;
import com.securepay.dto.AuditLogResponse;
import com.securepay.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UUID merchantId, String actor, String action, String resourceType,
                       String resourceId, String ipAddress, int httpStatus, long executionTimeMs) {
        AuditLog auditLog = new AuditLog(merchantId, actor, action, resourceType, resourceId, ipAddress, httpStatus, executionTimeMs);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogsForMerchant(UUID merchantId) {
        return auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getMerchantId(),
                        log.getActor(),
                        log.getAction(),
                        log.getResourceType(),
                        log.getResourceId(),
                        log.getIpAddress(),
                        log.getHttpStatus(),
                        log.getExecutionTimeMs(),
                        log.getCreatedAt()
                ))
                .toList();
    }
}
