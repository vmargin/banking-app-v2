package com.vmargin.banking.util;

import java.math.BigDecimal;

public final class MoneyValidation {
    public static final BigDecimal MAXIMUM = new BigDecimal("9999999999999.99");

    private MoneyValidation() {
    }

    public static void amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2
            || value.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException(
                "Amount must be positive, within the limit, and use at most two decimals."
            );
        }
    }

    public static void details(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Details must contain 1 to 255 characters.");
        }
    }

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.length() > 24) {
            throw new IllegalArgumentException("Enter a valid amount.");
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            amount(value);
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid amount.");
        }
    }
}
