package com.vmargin.banking.service;

import com.vmargin.banking.model.SavingsGoal;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.SavingsGoalRepository;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class SavingsGoalService {
    private final SavingsGoalRepository repository;

    public SavingsGoalService(SavingsGoalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Savings goal repository is required");
    }

    public List<SavingsGoal> list(User owner) throws SQLException {
        return repository.findByOwner(Objects.requireNonNull(owner, "Owner is required").getId());
    }

    public SavingsGoal get(User owner, long goalId) throws SQLException {
        if (owner == null || goalId < 1) {
            return null;
        }
        return repository.findByOwnerAndId(owner.getId(), goalId);
    }

    public SavingsGoal create(User owner, String suppliedName, String rawTarget) throws SQLException {
        Objects.requireNonNull(owner, "Owner is required");
        String name = suppliedName == null ? "" : suppliedName.trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new IllegalArgumentException("Goal name must be between 1 and 40 characters.");
        }
        BigDecimal target = MoneyValidation.parse(rawTarget);
        return repository.create(owner.getId(), name, target);
    }

    public void move(User owner, long goalId, BigDecimal amount, boolean contribution, String reference)
        throws SQLException {
        Objects.requireNonNull(owner, "Owner is required");
        MoneyValidation.amount(amount);
        repository.moveFromAccount(owner.getId(), owner.getBankAccount().getId(), goalId, amount,
            contribution, LocalDateTime.now(), reference);
    }
}
