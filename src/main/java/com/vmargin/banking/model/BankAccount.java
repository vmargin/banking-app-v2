package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.util.Objects;

/** A customer's bank account. Database IDs remain internal; account numbers are customer-facing strings. */
public class BankAccount {
    private final long id;
    private final long ownerId;
    private final String accountId;
    private final String holderName;
    private final String accountName;
    private final String accountType;
    private final String currencyCode;
    private final String status;
    private final boolean primary;
    private BigDecimal balance;

    public BankAccount(String accountId, String holderName, BigDecimal balance) {
        this(0, 0, accountId, holderName, "Everyday account", "CHECKING", "PHP", "ACTIVE", true, balance);
    }

    public BankAccount(long id, long ownerId, String accountId, String holderName, String accountName,
                       String accountType, String currencyCode, String status, boolean primary,
                       BigDecimal balance) {
        if (id < 0 || ownerId < 0) {
            throw new IllegalArgumentException("Account identifiers cannot be negative");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Holder name is required");
        }
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }
        if (balance == null || balance.signum() < 0 || balance.scale() > 2) {
            throw new IllegalArgumentException("Account balance must be nonnegative with at most two decimals");
        }
        this.id = id;
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.holderName = holderName;
        this.accountName = accountName;
        this.accountType = Objects.requireNonNull(accountType, "Account type is required");
        this.currencyCode = Objects.requireNonNull(currencyCode, "Currency is required");
        this.status = Objects.requireNonNull(status, "Account status is required");
        this.primary = primary;
        this.balance = balance;
    }

    public long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    /** Compatibility name retained for existing templates; this is the public account number. */
    public String getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPrimary() {
        return primary;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public String toString() {
        return "Acc ID: " + accountId + "\nHolder Name: " + holderName + "\nBalance: " + balance;
    }

    public void deposit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimals");
        }
        this.balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimals");
        }
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Amount cannot be greater than balance");
        }
        this.balance = balance.subtract(amount);
    }
}
