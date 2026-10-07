package com.vmargin.banking.service;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.AccountRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AccountService {
    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Account repository is required");
    }

    public List<BankAccount> list(User owner) throws SQLException {
        return repository.findByOwner(Objects.requireNonNull(owner, "Account owner is required").getId());
    }

    public Optional<BankAccount> get(User owner, long accountId) throws SQLException {
        Objects.requireNonNull(owner, "Account owner is required");
        if (accountId < 1) {
            return Optional.empty();
        }
        return repository.findByIdAndOwner(owner.getId(), accountId);
    }

    public Optional<BankAccount> recipient(String suppliedAccountNumber) throws SQLException {
        String accountNumber = normalizeAccountNumber(suppliedAccountNumber);
        return repository.findByAccountNumber(accountNumber)
            .filter(account -> "ACTIVE".equals(account.getStatus()));
    }

    public BankAccount openSavings(User owner, String suppliedName) throws SQLException {
        return open(owner, suppliedName, "PHP");
    }

    public BankAccount open(User owner, String suppliedName, String currencyCode) throws SQLException {
        Objects.requireNonNull(owner, "Account owner is required");
        String name = suppliedName == null ? "" : suppliedName.trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new IllegalArgumentException("Account name must be between 1 and 40 characters.");
        }
        String accountType = switch (currencyCode == null ? "" : currencyCode) {
            case "PHP" -> "SAVINGS";
            case "USD" -> "FOREIGN_CURRENCY";
            default -> throw new IllegalArgumentException("Choose PHP savings or a USD demo account.");
        };
        return repository.create(owner.getId(), owner.getFullName(), name, accountType, currencyCode,
            BigDecimal.ZERO.setScale(2));
    }

    public static String normalizeAccountNumber(String suppliedAccountNumber) {
        if (suppliedAccountNumber == null) {
            throw new IllegalArgumentException("Enter a 12-digit Cash - G demo account number.");
        }
        String normalized = suppliedAccountNumber.trim().replaceAll("\\s", "");
        if (!normalized.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("Enter a 12-digit Cash - G demo account number.");
        }
        return normalized;
    }
}
