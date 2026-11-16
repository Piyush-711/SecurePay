package com.securepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.domain.entity.Merchant;
import com.securepay.domain.entity.PaymentTransaction;
import com.securepay.domain.enums.PaymentStatus;
import com.securepay.domain.enums.WebhookEventType;
import com.securepay.dto.WebhookEventRequest;
import com.securepay.dto.WebhookEventResponse;
import com.securepay.repository.MerchantRepository;
import com.securepay.repository.PaymentTransactionRepository;
import com.securepay.repository.WebhookEventRepository;
import com.securepay.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebhookOutOfOrderStateValidationTest {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PaymentTransactionRepository paymentRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    private PaymentTransaction payment;

    @BeforeEach
    void setup() {
        Merchant merchant = new Merchant("Webhook Test Merchant", "webhook@merchant.com");
        merchant = merchantRepository.save(merchant);

        payment = new PaymentTransaction(merchant, new BigDecimal("150.00"), "USD", "buyer@test.com", "Test Order");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(new BigDecimal("150.00"));
        payment = paymentRepository.save(payment);
    }

    @Test
    @DisplayName("Should detect and ignore delayed PAYMENT_AUTHORIZED event when payment is already CAPTURED")
    void shouldIgnoreDelayedAuthorizeEventWhenAlreadyCaptured() {
        WebhookEventRequest request = new WebhookEventRequest(
                "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_AUTHORIZED,
                payment.getId(),
                1L, // earlier sequence number
                new BigDecimal("150.00"),
                null
        );

        WebhookEventResponse response = webhookService.processWebhookEvent(request, "127.0.0.1");

        assertThat(response.status()).isEqualTo("IGNORED_OUT_OF_ORDER");
        assertThat(response.notes()).contains("Out-of-order event");

        // Verify transaction state was preserved in CAPTURED (not reverted to AUTHORIZED)
        PaymentTransaction updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    @DisplayName("Should reject invalid state transition such as refunding a payment before capture")
    void shouldRejectInvalidStateTransition() {
        // Create a fresh uncaptured payment
        Merchant merchant = payment.getMerchant();
        PaymentTransaction freshPayment = new PaymentTransaction(merchant, new BigDecimal("80.00"), "EUR", "buyer2@test.com", "Order 2");
        freshPayment.setStatus(PaymentStatus.AUTHORIZED);
        freshPayment = paymentRepository.save(freshPayment);

        WebhookEventRequest invalidRefund = new WebhookEventRequest(
                "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_REFUNDED,
                freshPayment.getId(),
                2L,
                new BigDecimal("80.00"),
                null
        );

        WebhookEventResponse response = webhookService.processWebhookEvent(invalidRefund, "127.0.0.1");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.notes()).contains("Out-of-order anomaly");

        PaymentTransaction check = paymentRepository.findById(freshPayment.getId()).orElseThrow();
        assertThat(check.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    @DisplayName("Should handle duplicate webhook event idempotently")
    void shouldHandleDuplicateWebhookIdempotently() {
        String eventId = "evt_dup_" + UUID.randomUUID();

        WebhookEventRequest firstRequest = new WebhookEventRequest(
                eventId,
                WebhookEventType.PAYMENT_REFUNDED,
                payment.getId(),
                2L,
                new BigDecimal("150.00"),
                null
        );

        WebhookEventResponse firstResp = webhookService.processWebhookEvent(firstRequest, "127.0.0.1");
        assertThat(firstResp.status()).isEqualTo("PROCESSED");

        // Send duplicate webhook event with same eventId
        WebhookEventResponse secondResp = webhookService.processWebhookEvent(firstRequest, "127.0.0.1");
        assertThat(secondResp.eventId()).isEqualTo(eventId);
        assertThat(secondResp.status()).isEqualTo("PROCESSED");
    }
}
