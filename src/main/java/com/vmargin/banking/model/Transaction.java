package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transaction {
    private final long id;
    private final long userId;
    private final long accountId;
    private final String accountName;
    private final String currencyCode;
    private final TransactionType type;
    private final TransactionCategory category;
    private final BigDecimal amount;
    private final String details;
    private final LocalDateTime occurredAt;
    private final String reference;

    public Transaction(
        long id,
        long userId,
        TransactionType type,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) {
        this(id, userId, type, amount, details, occurredAt, null);
    }

    public Transaction(long id, long userId, TransactionType type, BigDecimal amount,
                       String details, LocalDateTime occurredAt, String reference) {
        this(id, userId, 0, "", "PHP", type, amount, details, occurredAt, reference,
            TransactionCategory.forType(type));
    }

    public Transaction(long id, long userId, TransactionType type, BigDecimal amount,
                       String details, LocalDateTime occurredAt, String reference, TransactionCategory category) {
        this(id, userId, 0, "", "PHP", type, amount, details, occurredAt, reference, category);
    }

    public Transaction(long id, long userId, long accountId, String accountName, String currencyCode,
                       TransactionType type, BigDecimal amount, String details,
                       LocalDateTime occurredAt, String reference) {
        this(id, userId, accountId, accountName, currencyCode, type, amount, details, occurredAt, reference,
            TransactionCategory.forType(type));
    }

    public Transaction(long id, long userId, long accountId, String accountName, String currencyCode,
                       TransactionType type, BigDecimal amount, String details,
                       LocalDateTime occurredAt, String reference, TransactionCategory category) {
        if (id < 0) {
            throw new IllegalArgumentException("Transaction ID cannot be negative");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (accountId < 0) {
            throw new IllegalArgumentException("Account ID cannot be negative");
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
        this.reference = reference;
        this.userId = userId;
        this.accountId = accountId;
        this.accountName = accountName == null ? "" : accountName;
        if (!"PHP".equals(currencyCode) && !"USD".equals(currencyCode)) {
            throw new IllegalArgumentException("Unsupported transaction currency");
        }
        this.currencyCode = currencyCode;
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null");
        this.category = Objects.requireNonNull(category, "Transaction category cannot be null");
        if (!category.isValidFor(type)) {
            throw new IllegalArgumentException("Transaction category does not match its type");
        }
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

    public long getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionCategory getCategory() {
        return category;
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

    public String getReference() {
        return reference;
    }
}
