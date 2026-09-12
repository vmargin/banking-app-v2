package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transaction {
    private final long id;
    private final long userId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String details;
    private final LocalDateTime occurredAt;

    public Transaction(
        long id,
        long userId,
        TransactionType type,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) {
        if (id < 0) {
            throw new IllegalArgumentException("Transaction ID cannot be negative");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }

        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                "Transaction amount cannot have more than 2 decimals"
            );
        }
        if (details == null || details.isBlank()) {
            throw new IllegalArgumentException("Transaction details are required");
        }

        this.id = id;
        this.userId = userId;
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null");
        this.amount = amount;
        this.details = details;
        this.occurredAt = Objects.requireNonNull(
            occurredAt,
            "Transaction time is required"
        );
    }
    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
