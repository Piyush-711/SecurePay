package com.securepay.service;

import com.securepay.domain.entity.Merchant;
import com.securepay.domain.entity.PaymentTransaction;
import com.securepay.domain.enums.PaymentAction;
import com.securepay.domain.enums.PaymentStatus;
import com.securepay.dto.AuthorizePaymentRequest;
import com.securepay.dto.CapturePaymentRequest;
import com.securepay.dto.PaymentResponse;
import com.securepay.dto.RefundPaymentRequest;
import com.securepay.exception.BadRequestException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.MerchantRepository;
import com.securepay.repository.PaymentTransactionRepository;
import com.securepay.statemachine.TransactionStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentTransactionRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionStateMachine stateMachine;
    private final AuditLogService auditLogService;

    public PaymentService(PaymentTransactionRepository paymentRepository,
                          MerchantRepository merchantRepository,
                          TransactionStateMachine stateMachine,
                          AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
        this.stateMachine = stateMachine;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PaymentResponse authorize(UUID merchantId, AuthorizePaymentRequest request, String actor, String ipAddress) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        PaymentTransaction transaction = new PaymentTransaction(
                merchant,
                request.amount(),
                request.currency(),
                request.customerEmail(),
                request.description()
        );

        stateMachine.validateAction(transaction, PaymentAction.AUTHORIZE);
        transaction.setStatus(PaymentStatus.AUTHORIZED);
        transaction = paymentRepository.save(transaction);

        auditLogService.record(
                merchantId,
                actor,
                "PAYMENT_AUTHORIZE",
                "PAYMENT",
                transaction.getId().toString(),
                ipAddress,
                201,
                0
        );

        return mapToResponse(transaction);
    }

    @Transactional
    public PaymentResponse capture(UUID merchantId, UUID paymentId, CapturePaymentRequest request, String actor, String ipAddress) {
        PaymentTransaction transaction = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + paymentId));

        stateMachine.validateAction(transaction, PaymentAction.CAPTURE);

        BigDecimal captureAmount = request != null && request.amount() != null
                ? request.amount()
                : transaction.getAmount().subtract(transaction.getCapturedAmount());

        if (captureAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Capture amount must be greater than zero");
        }

        BigDecimal newTotalCaptured = transaction.getCapturedAmount().add(captureAmount);
        if (newTotalCaptured.compareTo(transaction.getAmount()) > 0) {
            throw new BadRequestException("Capture amount (" + captureAmount + ") exceeds remaining authorized balance (" +
                    transaction.getAmount().subtract(transaction.getCapturedAmount()) + ")");
        }

        transaction.setCapturedAmount(newTotalCaptured);
        transaction.setStatus(PaymentStatus.CAPTURED);
        if (request != null && request.description() != null) {
            transaction.setDescription(request.description());
        }

        transaction = paymentRepository.save(transaction);

        auditLogService.record(
                merchantId,
                actor,
                "PAYMENT_CAPTURE",
                "PAYMENT",
                transaction.getId().toString(),
                ipAddress,
                200,
                0
        );

        return mapToResponse(transaction);
    }

    @Transactional
    public PaymentResponse refund(UUID merchantId, UUID paymentId, RefundPaymentRequest request, String actor, String ipAddress) {
        PaymentTransaction transaction = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + paymentId));

        stateMachine.validateAction(transaction, PaymentAction.REFUND);

        BigDecimal maxRefundable = transaction.getCapturedAmount().subtract(transaction.getRefundedAmount());
        if (maxRefundable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment has already been fully refunded");
        }

        BigDecimal refundAmount = request != null && request.amount() != null
                ? request.amount()
                : maxRefundable;

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero");
        }

        if (refundAmount.compareTo(maxRefundable) > 0) {
            throw new BadRequestException("Refund amount (" + refundAmount + ") exceeds maximum refundable captured amount (" + maxRefundable + ")");
        }

        BigDecimal newTotalRefunded = transaction.getRefundedAmount().add(refundAmount);
        transaction.setRefundedAmount(newTotalRefunded);

        if (newTotalRefunded.compareTo(transaction.getCapturedAmount()) >= 0) {
            transaction.setStatus(PaymentStatus.REFUNDED);
        } else {
            transaction.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }

        transaction = paymentRepository.save(transaction);

        auditLogService.record(
                merchantId,
                actor,
                "PAYMENT_REFUND",
                "PAYMENT",
                transaction.getId().toString(),
                ipAddress,
                200,
                0
        );

        return mapToResponse(transaction);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID merchantId, UUID paymentId) {
        PaymentTransaction transaction = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + paymentId));
        return mapToResponse(transaction);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(UUID merchantId) {
        return paymentRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private PaymentResponse mapToResponse(PaymentTransaction t) {
        return new PaymentResponse(
                t.getId(),
                t.getMerchant().getId(),
                t.getAmount(),
                t.getCurrency(),
                t.getStatus(),
                t.getCustomerEmail(),
                t.getDescription(),
                t.getCapturedAmount(),
                t.getRefundedAmount(),
                t.getFailureReason(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
