package com.vmargin.banking.service;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransactionRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class TransactionHistoryService {

    private final TransactionRepository repository;

    public TransactionHistoryService(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(
            repository,
            "Transaction repository is required"
        );
    }

    public List<Transaction> getHistory(User user) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        return repository.findByUserId(user.getId());
    }
}
