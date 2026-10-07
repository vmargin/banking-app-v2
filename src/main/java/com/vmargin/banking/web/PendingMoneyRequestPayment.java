package com.vmargin.banking.web;

import java.time.LocalDateTime;
import java.util.Objects;

/** Server-owned, short-lived review state for paying a local money request. */
public record PendingMoneyRequestPayment(long requestId, long sourceAccountId, String token,
                                         String paymentReference, LocalDateTime expiresAt) {
    public PendingMoneyRequestPayment {
        if (requestId < 1 || sourceAccountId < 1) {
            throw new IllegalArgumentException("Payment review identifiers must be positive.");
        }
        Objects.requireNonNull(token, "Payment review token is required");
        Objects.requireNonNull(paymentReference, "Payment reference is required");
        Objects.requireNonNull(expiresAt, "Payment review expiry is required");
    }
}
