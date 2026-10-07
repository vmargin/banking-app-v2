package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsGoal(long id, long ownerId, String name, BigDecimal targetAmount,
                          BigDecimal savedAmount, LocalDateTime createdAt) {
    public int progressPercent() {
        if (targetAmount.signum() <= 0) {
            return 0;
        }
        if (savedAmount.compareTo(targetAmount) >= 0) {
            return 100;
        }
        return Math.min(100, savedAmount.multiply(BigDecimal.valueOf(100))
            .divide(targetAmount, 0, java.math.RoundingMode.DOWN).intValueExact());
    }
}
