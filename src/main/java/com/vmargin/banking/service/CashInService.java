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
        return cashIn(user, amount, details, java.util.UUID.randomUUID().toString());
    }

    public BigDecimal cashIn(User user, BigDecimal amount, String details, String reference)
        throws SQLException {
        Objects.requireNonNull(user, "User is required");
        return cashIn(user, user.getBankAccount().getId(), amount, details, reference);
    }

    public BigDecimal cashIn(User user, long accountId, BigDecimal amount, String details, String reference)
        throws SQLException {
        Objects.requireNonNull(user, "User is required");
        validateAmount(amount);
        validateDetails(details);

        return repository.cashInAccount(
            user.getId(),
            accountId,
            amount,
            details,
            LocalDateTime.now(),
            reference
        );
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
        if (amount.compareTo(com.vmargin.banking.util.MoneyValidation.MAXIMUM) > 0) {
            throw new InvalidCashInException("Cash-in amount exceeds the supported limit");
        }
    }

    private void validateDetails(String details) {
        if (details == null || details.isBlank() || details.length() > 255) {
            throw new InvalidCashInException("Cash-in details are required");
        }
    }
}
