package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User {
    private final long id;
    private final String mobileNumber;
    private final String pin;
    private final String fullName;
    private final BankAccount bankAccount;
    private final UserRole role;
    private final List<Transaction> transactions;

    public User(
        long id,
        String mobileNumber,
        String pin,
        String fullName,
        BankAccount bankAccount
    ) {
        this(id, mobileNumber, pin, fullName, bankAccount, UserRole.USER);
    }

    public User(
        long id,
        String mobileNumber,
        String pin,
        String fullName,
        BankAccount bankAccount,
        UserRole role
    ) {
        if (id < 0) {
            throw new IllegalArgumentException("User ID cannot be negative");
        }
        if (mobileNumber == null || mobileNumber.isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        if (pin == null || pin.isBlank()) {
            throw new IllegalArgumentException("PIN is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        this.id = id;
        this.mobileNumber = mobileNumber;
        this.pin = pin;
        this.fullName = fullName;
        this.bankAccount = Objects.requireNonNull(
            bankAccount,
            "Bank account is required"
        );
        this.role = Objects.requireNonNull(role, "User role is required");
        this.transactions = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPinForPersistence() {
        return pin;
    }

    public boolean matchesPin(String candidatePin) {
        return pin.equals(candidatePin);
    }

    public String getFullName() {
        return fullName;
    }

    public BigDecimal getBalance() {
        return bankAccount.getBalance();
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public List<Transaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public void addTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction is required");

        if (transaction.getUserId() != id) {
            throw new IllegalArgumentException(
                "Transaction user ID must match this user"
            );
        }

        transactions.add(transaction);
    }
}
