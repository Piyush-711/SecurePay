package com.securepay.controller;

import com.securepay.dto.AuthorizePaymentRequest;
import com.securepay.dto.CapturePaymentRequest;
import com.securepay.dto.PaymentResponse;
import com.securepay.dto.RefundPaymentRequest;
import com.securepay.security.MerchantPrincipal;
import com.securepay.security.SecurityUtils;
import com.securepay.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'MERCHANT_API')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(@Valid @RequestBody AuthorizePaymentRequest request,
                                                     HttpServletRequest httpRequest) {
        MerchantPrincipal principal = SecurityUtils.getCurrentPrincipal();
        String ip = getClientIp(httpRequest);
        PaymentResponse response = paymentService.authorize(
                principal.getMerchantId(),
                request,
                principal.getIdentifier(),
                ip
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capture(@PathVariable("paymentId") UUID paymentId,
                                                   @Valid @RequestBody(required = false) CapturePaymentRequest request,
                                                   HttpServletRequest httpRequest) {
        MerchantPrincipal principal = SecurityUtils.getCurrentPrincipal();
        String ip = getClientIp(httpRequest);
        PaymentResponse response = paymentService.capture(
                principal.getMerchantId(),
                paymentId,
                request,
                principal.getIdentifier(),
                ip
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable("paymentId") UUID paymentId,
                                                  @Valid @RequestBody(required = false) RefundPaymentRequest request,
                                                  HttpServletRequest httpRequest) {
        MerchantPrincipal principal = SecurityUtils.getCurrentPrincipal();
        String ip = getClientIp(httpRequest);
        PaymentResponse response = paymentService.refund(
                principal.getMerchantId(),
                paymentId,
                request,
                principal.getIdentifier(),
                ip
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable("paymentId") UUID paymentId) {
        UUID merchantId = SecurityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(paymentService.getPayment(merchantId, paymentId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments() {
        UUID merchantId = SecurityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(paymentService.listPayments(merchantId));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0] : request.getRemoteAddr();
    }
}
