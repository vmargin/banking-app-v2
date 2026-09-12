package com.vmargin.banking.service;

import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.CashInRepository;
import com.vmargin.banking.service.exception.InvalidCashInException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

public class CashInService {

    private final CashInRepository repository;

    public CashInService(CashInRepository repository) {
        this.repository = Objects.requireNonNull(
            repository,
            "Cash-in repository is required"
        );
    }

    public BigDecimal cashIn(User user, BigDecimal amount, String details)
        throws SQLException {
        Objects.requireNonNull(user, "User is required");
        validateAmount(amount);
        validateDetails(details);

        BigDecimal updatedBalance = repository.cashIn(
            user.getId(),
            amount,
            details,
            LocalDateTime.now()
        );
        user.getBankAccount().deposit(amount);
        return updatedBalance;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidCashInException("Cash-in amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new InvalidCashInException(
                "Cash-in amount cannot have more than 2 decimals"
            );
        }
    }

    private void validateDetails(String details) {
        if (details == null || details.isBlank()) {
            throw new InvalidCashInException("Cash-in details are required");
        }
    }
}
