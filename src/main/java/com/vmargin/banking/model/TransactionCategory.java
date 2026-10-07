package com.vmargin.banking.model;

import java.util.Objects;

/** Stable ledger categories used by activity views and spending insights. */
public enum TransactionCategory {
    FOOD_AND_DINING("Food & Dining", true),
    SHOPPING("Shopping", true),
    TRANSPORTATION("Transportation", true),
    BILLS_AND_UTILITIES("Bills & Utilities", true),
    ENTERTAINMENT("Entertainment", true),
    OTHER("Other", true),
    TRANSFERS("Transfers", false),
    SAVINGS("Savings", false),
    INCOME("Income", false);

    private final String displayName;
    private final boolean spending;

    TransactionCategory(String displayName, boolean spending) {
        this.displayName = displayName;
        this.spending = spending;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isSpending() {
        return spending;
    }

    public static TransactionCategory forType(TransactionType type) {
        Objects.requireNonNull(type, "Transaction type is required");
        return switch (type) {
            case CASH_IN -> INCOME;
            case TRANSFER_SENT, TRANSFER_RECEIVED -> TRANSFERS;
            case SAVINGS_CONTRIBUTION, SAVINGS_WITHDRAWAL -> SAVINGS;
            case BILL_PAYMENT -> BILLS_AND_UTILITIES;
            case CARD_PURCHASE -> OTHER;
        };
    }

    public boolean isValidFor(TransactionType type) {
        Objects.requireNonNull(type, "Transaction type is required");
        return switch (type) {
            case CASH_IN -> this == INCOME;
            case TRANSFER_SENT, TRANSFER_RECEIVED -> this == TRANSFERS;
            case SAVINGS_CONTRIBUTION, SAVINGS_WITHDRAWAL -> this == SAVINGS;
            case BILL_PAYMENT -> this == BILLS_AND_UTILITIES;
            case CARD_PURCHASE -> spending && this != BILLS_AND_UTILITIES;
        };
    }
}
