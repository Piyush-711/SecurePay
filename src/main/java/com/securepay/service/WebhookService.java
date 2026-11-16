package com.securepay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.domain.entity.PaymentTransaction;
import com.securepay.domain.entity.WebhookEvent;
import com.securepay.domain.enums.PaymentStatus;
import com.securepay.domain.enums.WebhookEventType;
import com.securepay.dto.WebhookEventRequest;
import com.securepay.dto.WebhookEventResponse;
import com.securepay.exception.BadRequestException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.PaymentTransactionRepository;
import com.securepay.repository.WebhookEventRepository;
import com.securepay.statemachine.TransactionStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final TransactionStateMachine stateMachine;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookEventRepository webhookEventRepository,
                          PaymentTransactionRepository paymentRepository,
                          TransactionStateMachine stateMachine,
                          AuditLogService auditLogService,
                          ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.stateMachine = stateMachine;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEventResponse processWebhookEvent(WebhookEventRequest request, String ipAddress) {
        // 1. Check duplicate webhook event (Idempotency)
        Optional<WebhookEvent> existingEvent = webhookEventRepository.findByEventId(request.eventId());
        if (existingEvent.isPresent()) {
            WebhookEvent event = existingEvent.get();
            return mapToResponse(event);
        }

        // 2. Fetch associated transaction
        PaymentTransaction transaction = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for webhook event: " + request.paymentId()));

        // 3. State machine validation for out-of-order events
        TransactionStateMachine.WebhookTransitionResult result = stateMachine.evaluateWebhookEvent(
                transaction,
                request.eventType(),
                request.sequenceNumber()
        );

        // 4. Update transaction state if allowed
        if (result.shouldUpdateState()) {
            transaction.setStatus(result.targetStatus());

            if (request.eventType() == WebhookEventType.PAYMENT_CAPTURED) {
                BigDecimal captureAmt = request.amount() != null ? request.amount() : transaction.getAmount();
                transaction.setCapturedAmount(captureAmt);
            } else if (request.eventType() == WebhookEventType.PAYMENT_REFUNDED) {
                BigDecimal refundAmt = request.amount() != null ? request.amount() : transaction.getCapturedAmount();
                transaction.setRefundedAmount(refundAmt);
            } else if (request.eventType() == WebhookEventType.PAYMENT_FAILED) {
                transaction.setFailureReason(request.failureReason() != null ? request.failureReason() : "Failed via webhook");
            }

            paymentRepository.save(transaction);
        }

        // 5. Serialize payload for record
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        // 6. Record Webhook Event
        WebhookEvent webhookEvent = new WebhookEvent(
                transaction.getMerchant(),
                request.eventId(),
                request.eventType(),
                transaction,
                request.sequenceNumber(),
                payloadJson,
                result.outcomeStatus(),
                result.explanation()
        );
        webhookEvent = webhookEventRepository.save(webhookEvent);

        // 7. Audit Logging
        auditLogService.record(
                transaction.getMerchant().getId(),
                "WEBHOOK_PROCESSOR",
                "WEBHOOK_" + request.eventType(),
                "WEBHOOK_EVENT",
                request.eventId(),
                ipAddress,
                result.outcomeStatus().equals("REJECTED") ? 422 : 200,
                0
        );

        return mapToResponse(webhookEvent);
    }

    private WebhookEventResponse mapToResponse(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getId(),
                event.getEventId(),
                event.getEventType().name(),
                event.getPaymentTransaction() != null ? event.getPaymentTransaction().getId() : null,
                event.getSequenceNumber(),
                event.getStatus(),
                event.getNotes(),
                event.getCreatedAt()
        );
    }
}
