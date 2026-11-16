package com.securepay.statemachine;

import com.securepay.domain.entity.PaymentTransaction;
import com.securepay.domain.enums.PaymentAction;
import com.securepay.domain.enums.PaymentStatus;
import com.securepay.domain.enums.WebhookEventType;
import com.securepay.exception.InvalidPaymentStateException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Component
public class TransactionStateMachine {

    // Valid forward transitions for internal API actions
    private static final Map<PaymentStatus, Set<PaymentAction>> VALID_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentAction.AUTHORIZE),
            PaymentStatus.AUTHORIZED, Set.of(PaymentAction.CAPTURE, PaymentAction.VOID),
            PaymentStatus.CAPTURED, Set.of(PaymentAction.REFUND),
            PaymentStatus.PARTIALLY_REFUNDED, Set.of(PaymentAction.REFUND),
            PaymentStatus.REFUNDED, Set.of(),
            PaymentStatus.VOIDED, Set.of(),
            PaymentStatus.FAILED, Set.of()
    );

    public void validateAction(PaymentTransaction transaction, PaymentAction action) {
        PaymentStatus currentStatus = transaction.getStatus();
        Set<PaymentAction> allowedActions = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (!allowedActions.contains(action)) {
            throw new InvalidPaymentStateException(
                    "Invalid action '" + action + "' for payment transaction in status '" + currentStatus + "'"
            );
        }
    }

    /**
     * Determines whether an incoming webhook event can transition the transaction state,
     * should be ignored as an out-of-order delayed event, or is an invalid transition.
     */
    public WebhookTransitionResult evaluateWebhookEvent(PaymentTransaction transaction, WebhookEventType eventType, Long eventSequence) {
        PaymentStatus currentStatus = transaction.getStatus();

        switch (eventType) {
            case PAYMENT_AUTHORIZED:
                if (currentStatus == PaymentStatus.CREATED) {
                    return WebhookTransitionResult.apply(PaymentStatus.AUTHORIZED, "Authorized via webhook");
                } else if (currentStatus == PaymentStatus.AUTHORIZED) {
                    return WebhookTransitionResult.alreadyApplied("Payment already authorized");
                } else if (currentStatus == PaymentStatus.CAPTURED || currentStatus == PaymentStatus.REFUNDED || currentStatus == PaymentStatus.PARTIALLY_REFUNDED) {
                    // Out-of-order! Authorize event arrived after capture/refund
                    return WebhookTransitionResult.ignoreOutOfOrder(
                            "Out-of-order event: PAYMENT_AUTHORIZED received for transaction already in '" + currentStatus + "' state"
                    );
                } else {
                    return WebhookTransitionResult.reject("Cannot authorize payment in status " + currentStatus);
                }

            case PAYMENT_CAPTURED:
                if (currentStatus == PaymentStatus.CREATED || currentStatus == PaymentStatus.AUTHORIZED) {
                    return WebhookTransitionResult.apply(PaymentStatus.CAPTURED, "Captured via webhook");
                } else if (currentStatus == PaymentStatus.CAPTURED) {
                    return WebhookTransitionResult.alreadyApplied("Payment already captured");
                } else if (currentStatus == PaymentStatus.REFUNDED || currentStatus == PaymentStatus.PARTIALLY_REFUNDED) {
                    return WebhookTransitionResult.ignoreOutOfOrder(
                            "Out-of-order event: PAYMENT_CAPTURED received for transaction already in '" + currentStatus + "' state"
                    );
                } else {
                    return WebhookTransitionResult.reject("Cannot capture payment in status " + currentStatus);
                }

            case PAYMENT_REFUNDED:
                if (currentStatus == PaymentStatus.CAPTURED || currentStatus == PaymentStatus.PARTIALLY_REFUNDED) {
                    return WebhookTransitionResult.apply(PaymentStatus.REFUNDED, "Refunded via webhook");
                } else if (currentStatus == PaymentStatus.REFUNDED) {
                    return WebhookTransitionResult.alreadyApplied("Payment already refunded");
                } else if (currentStatus == PaymentStatus.CREATED || currentStatus == PaymentStatus.AUTHORIZED) {
                    return WebhookTransitionResult.reject(
                            "Out-of-order anomaly: PAYMENT_REFUNDED received for payment before capture (current status: " + currentStatus + ")"
                    );
                } else {
                    return WebhookTransitionResult.reject("Cannot refund payment in status " + currentStatus);
                }

            case PAYMENT_FAILED:
                if (currentStatus == PaymentStatus.CREATED || currentStatus == PaymentStatus.AUTHORIZED) {
                    return WebhookTransitionResult.apply(PaymentStatus.FAILED, "Payment failed via webhook");
                } else if (currentStatus == PaymentStatus.FAILED) {
                    return WebhookTransitionResult.alreadyApplied("Payment already marked as failed");
                } else {
                    return WebhookTransitionResult.ignoreOutOfOrder(
                            "Out-of-order event: PAYMENT_FAILED received after payment reached state " + currentStatus
                    );
                }

            default:
                return WebhookTransitionResult.reject("Unknown webhook event type: " + eventType);
        }
    }

    public record WebhookTransitionResult(
            boolean shouldUpdateState,
            PaymentStatus targetStatus,
            String outcomeStatus, // PROCESSED, IGNORED_OUT_OF_ORDER, REJECTED
            String explanation
    ) {
        public static WebhookTransitionResult apply(PaymentStatus targetStatus, String reason) {
            return new WebhookTransitionResult(true, targetStatus, "PROCESSED", reason);
        }

        public static WebhookTransitionResult alreadyApplied(String reason) {
            return new WebhookTransitionResult(false, null, "PROCESSED", reason);
        }

        public static WebhookTransitionResult ignoreOutOfOrder(String reason) {
            return new WebhookTransitionResult(false, null, "IGNORED_OUT_OF_ORDER", reason);
        }

        public static WebhookTransitionResult reject(String reason) {
            return new WebhookTransitionResult(false, null, "REJECTED", reason);
        }
    }
}
