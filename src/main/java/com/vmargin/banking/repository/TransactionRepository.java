package com.vmargin.banking.repository;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.MonthlyTransactionInsights;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransactionRepository {

    Transaction save(Transaction transaction) throws SQLException;

    List<Transaction> findByUserId(long userId) throws SQLException;

    default TransactionActivity findActivity(long userId, TransactionFilter filter, int page, int pageSize)
        throws SQLException {
        throw new UnsupportedOperationException("Activity queries are not supported");
    }

    default MonthlyTransactionInsights findMonthlyInsights(long userId, YearMonth month) throws SQLException {
        throw new UnsupportedOperationException("Monthly insights are not supported");
    }

    default List<Transaction> findStatement(long userId, TransactionFilter filter, int limit) throws SQLException {
        throw new UnsupportedOperationException("Statement export is not supported");
    }

    default Optional<Transaction> findReceipt(long userId, String reference) throws SQLException {
        throw new UnsupportedOperationException("Receipt lookup is not supported");
    }

    default Set<Long> findReadNotificationIds(long userId, List<Long> transactionIds) throws SQLException {
        throw new UnsupportedOperationException("Notification read state is not supported");
    }

    default void markNotificationsRead(long userId, List<Long> transactionIds) throws SQLException {
        throw new UnsupportedOperationException("Notification read state is not supported");
    }

    default List<Transaction> findAll() throws SQLException {
        throw new UnsupportedOperationException("Listing transactions is not supported");
    }
}
