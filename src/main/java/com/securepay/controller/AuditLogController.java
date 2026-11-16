package com.securepay.controller;

import com.securepay.dto.AuditLogResponse;
import com.securepay.security.SecurityUtils;
import com.securepay.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('MERCHANT_ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs() {
        UUID merchantId = SecurityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(auditLogService.getLogsForMerchant(merchantId));
    }
}
