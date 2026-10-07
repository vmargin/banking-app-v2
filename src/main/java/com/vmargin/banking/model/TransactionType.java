package com.vmargin.banking.model;

public enum TransactionType {
    CASH_IN,
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    CARD_PURCHASE,
    SAVINGS_CONTRIBUTION,
    SAVINGS_WITHDRAWAL,
    BILL_PAYMENT;

    public boolean isIncoming() {
        return this == CASH_IN || this == TRANSFER_RECEIVED || this == SAVINGS_WITHDRAWAL;
    }

    public boolean isIncome() {
        return this == CASH_IN || this == TRANSFER_RECEIVED;
    }

    public boolean isSpending() {
        return this == CARD_PURCHASE || this == BILL_PAYMENT;
    }

    public String displayName() {
        return switch (this) {
            case CASH_IN -> "Cash-in";
            case TRANSFER_SENT -> "Transfer sent";
            case TRANSFER_RECEIVED -> "Transfer received";
            case CARD_PURCHASE -> "Card purchase";
            case SAVINGS_CONTRIBUTION -> "Savings contribution";
            case SAVINGS_WITHDRAWAL -> "Savings withdrawal";
            case BILL_PAYMENT -> "Simulated bill payment";
        };
    }

    public String noticeTitle() {
        return switch (this) {
            case CASH_IN -> "Cash-in recorded";
            case TRANSFER_SENT -> "Transfer sent";
            case TRANSFER_RECEIVED -> "Transfer received";
            case CARD_PURCHASE -> "Card purchase recorded";
            case SAVINGS_CONTRIBUTION -> "Savings moved to a goal";
            case SAVINGS_WITHDRAWAL -> "Savings returned to available balance";
            case BILL_PAYMENT -> "Demo bill entry recorded";
        };
    }
}
