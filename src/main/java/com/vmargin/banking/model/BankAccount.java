package com.vmargin.banking.model;
import java.math.BigDecimal;

public class BankAccount {
    private String accountId;
    private String holderName;
    private BigDecimal balance;

    public BankAccount(String accountId, String holderName, BigDecimal balance) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Holder name is required");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Opening balance is required");
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Opening balance cannot be negative");
        }
        if (balance.scale() > 2) {
            throw new IllegalArgumentException(
                "Opening balance cannot have more than 2 decimals");
        }
        this.accountId = accountId;
        this.holderName = holderName;
        this.balance = balance;
    }
    public String getAccountId() {
        return accountId;
    }
    public String getHolderName() {
        return holderName;
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
