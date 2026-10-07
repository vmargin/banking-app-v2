package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Stores only safe card display data. A demo card has no PAN, expiry or security code. */
public record BankCard(long id, long ownerId, long accountId, String accountName, String cardName,
                       String brand, String cardType, String lastFour, String status,
                       BigDecimal dailyLimit, boolean onlineEnabled, LocalDateTime createdAt) {
    public BankCard {
        if (id < 1 || ownerId < 1 || accountId < 1) {
            throw new IllegalArgumentException("Card identifiers must be positive");
        }
        Objects.requireNonNull(accountName, "Account name is required");
        Objects.requireNonNull(cardName, "Card name is required");
        Objects.requireNonNull(brand, "Card brand is required");
        Objects.requireNonNull(cardType, "Card type is required");
        if (lastFour == null || !lastFour.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Card display digits must contain four numbers");
        }
        Objects.requireNonNull(status, "Card status is required");
        Objects.requireNonNull(dailyLimit, "Card daily limit is required");
        Objects.requireNonNull(createdAt, "Card creation time is required");
    }

    public BankCard(long id, long ownerId, long accountId, String accountName, String cardName,
                    String brand, String cardType, String lastFour, String status,
                    BigDecimal dailyLimit, LocalDateTime createdAt) {
        this(id, ownerId, accountId, accountName, cardName, brand, cardType, lastFour, status,
            dailyLimit, true, createdAt);
    }

    public boolean isFrozen() {
        return "FROZEN".equals(status);
    }
}
