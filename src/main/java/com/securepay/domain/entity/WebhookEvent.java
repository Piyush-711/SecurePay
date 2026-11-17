package com.securepay.domain.entity;

import com.securepay.domain.enums.WebhookEventType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_event_id", columnList = "eventId", unique = true)
})
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WebhookEventType eventType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_id")
    private PaymentTransaction paymentTransaction;

    @Column(nullable = false)
    private Long sequenceNumber = 1L;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false, length = 30)
    private String status; // PROCESSED, IGNORED_OUT_OF_ORDER, REJECTED

    @Column(length = 255)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WebhookEvent() {}

    public WebhookEvent(Merchant merchant, String eventId, WebhookEventType eventType,
                        PaymentTransaction paymentTransaction, Long sequenceNumber,
                        String payload, String status, String notes) {
        this.merchant = merchant;
        this.eventId = eventId;
        this.eventType = eventType;
        this.paymentTransaction = paymentTransaction;
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
        this.status = status;
        this.notes = notes;
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

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public WebhookEventType getEventType() {
        return eventType;
    }

    public void setEventType(WebhookEventType eventType) {
        this.eventType = eventType;
    }

    public PaymentTransaction getPaymentTransaction() {
        return paymentTransaction;
    }

    public void setPaymentTransaction(PaymentTransaction paymentTransaction) {
        this.paymentTransaction = paymentTransaction;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
