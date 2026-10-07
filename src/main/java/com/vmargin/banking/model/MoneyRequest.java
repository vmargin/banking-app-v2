package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** A local request between two Nexa demo customers. It is not a payment instruction. */
public record MoneyRequest(long id, String reference, long requesterId, String requesterName,
                           long requesterAccountId, String requesterAccountName, String requesterAccountLastFour,
                           long payerId, String payerName, BigDecimal amount, String currencyCode,
                           String note, MoneyRequestStatus status, LocalDateTime createdAt,
                           String paymentReference) {
    public MoneyRequest {
        if (id < 1 || requesterId < 1 || requesterAccountId < 1 || payerId < 1 || requesterId == payerId) {
            throw new IllegalArgumentException("Money request identifiers are invalid.");
        }
        Objects.requireNonNull(reference, "Request reference is required");
        Objects.requireNonNull(requesterName, "Requester name is required");
        Objects.requireNonNull(requesterAccountName, "Requester account name is required");
        if (requesterAccountLastFour == null || !requesterAccountLastFour.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Account display digits must contain four numbers.");
        }
        Objects.requireNonNull(payerName, "Payer name is required");
        Objects.requireNonNull(amount, "Request amount is required");
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Request amount is invalid.");
        }
        Objects.requireNonNull(currencyCode, "Request currency is required");
        note = note == null ? "" : note;
        Objects.requireNonNull(status, "Request status is required");
        Objects.requireNonNull(createdAt, "Request date is required");
    }

    public boolean isPending() {
        return status == MoneyRequestStatus.PENDING;
    }
}
