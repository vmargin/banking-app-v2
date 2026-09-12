package com.vmargin.banking.service;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransactionRepository;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.AccessDeniedException;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AdminService(
        UserRepository userRepository,
        TransactionRepository transactionRepository
    ) {
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "User repository is required"
        );
        this.transactionRepository = Objects.requireNonNull(
            transactionRepository,
            "Transaction repository is required"
        );
    }

    public List<User> getAllUsers(User requester) throws SQLException {
        requireAdmin(requester);
        return userRepository.findAll();
    }

    public List<Transaction> getAllTransactions(User requester) throws SQLException {
        requireAdmin(requester);
        return transactionRepository.findAll();
    }

    public List<Transaction> getTransactionsForUser(User requester, long userId)
        throws SQLException {
        requireAdmin(requester);
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        return transactionRepository.findByUserId(userId);
    }

    private void requireAdmin(User requester) {
        if (requester == null || !requester.isAdmin()) {
            throw new AccessDeniedException("Administrator access is required");
        }
    }
}
