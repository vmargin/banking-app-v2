package com.vmargin.banking.service;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransactionRepository;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TransactionHistoryService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_PAGE_INDEX = 100_000;
    public static final int MAX_STATEMENT_ROWS = 10_000;

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

    public TransactionActivity getActivity(User user, TransactionFilter filter, int page, int pageSize)
        throws SQLException {
        Objects.requireNonNull(user, "User is required");
        Objects.requireNonNull(filter, "Transaction filter is required");
        if (page < 0 || page > MAX_PAGE_INDEX) {
            throw new IllegalArgumentException("Page number is outside the supported range.");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive.");
        }
        return repository.findActivity(user.getId(), filter, page, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    public Set<Long> getReadNotificationIds(User user, List<Transaction> notices) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        List<Long> ids = notificationIds(user, notices);
        return ids.isEmpty() ? Set.of() : repository.findReadNotificationIds(user.getId(), ids);
    }

    public void markRecentNotificationsRead(User user, List<Transaction> notices) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        List<Long> ids = notificationIds(user, notices);
        if (!ids.isEmpty()) {
            repository.markNotificationsRead(user.getId(), ids);
        }
    }

    public MonthlyTransactionInsights getMonthlyInsights(User user, YearMonth month) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        Objects.requireNonNull(month, "Month is required");
        if (month.getYear() < 1 || month.getYear() > 9998) {
            throw new IllegalArgumentException("The selected month is outside the supported range.");
        }
        return repository.findMonthlyInsights(user.getId(), month);
    }

    public List<Transaction> getStatement(User user, TransactionFilter filter) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        Objects.requireNonNull(filter, "Transaction filter is required");
        return repository.findStatement(user.getId(), filter, MAX_STATEMENT_ROWS);
    }

    public Optional<Transaction> getReceipt(User user, String reference) throws SQLException {
        Objects.requireNonNull(user, "User is required");
        if (reference == null || reference.isBlank() || reference.length() > 36) {
            return Optional.empty();
        }
        return repository.findReceipt(user.getId(), reference);
    }

    private List<Long> notificationIds(User user, List<Transaction> notices) {
        List<Transaction> entries = List.copyOf(Objects.requireNonNull(notices, "Notices are required"));
        for (Transaction notice : entries) {
            if (notice.getUserId() != user.getId() || notice.getId() <= 0) {
                throw new IllegalArgumentException("Notification entries must belong to the signed-in account");
            }
        }
        return entries.stream().map(Transaction::getId).distinct().toList();
    }
}
