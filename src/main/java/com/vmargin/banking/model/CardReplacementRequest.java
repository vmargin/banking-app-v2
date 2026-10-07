package com.vmargin.banking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** A persisted local request record; it does not order a physical or network-issued card. */
public record CardReplacementRequest(long id, long cardId, String cardName, String lastFour,
                                     CardReplacementReason reason, String status, String reference,
                                     LocalDateTime createdAt) {
    public CardReplacementRequest {
        if (id < 1 || cardId < 1) {
            throw new IllegalArgumentException("Replacement request identifiers must be positive.");
        }
        Objects.requireNonNull(cardName, "Card name is required");
        if (lastFour == null || !lastFour.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Card display digits must contain four numbers.");
        }
        Objects.requireNonNull(reason, "Replacement reason is required");
        Objects.requireNonNull(status, "Replacement request status is required");
        Objects.requireNonNull(reference, "Replacement request reference is required");
        Objects.requireNonNull(createdAt, "Replacement request date is required");
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }
}
