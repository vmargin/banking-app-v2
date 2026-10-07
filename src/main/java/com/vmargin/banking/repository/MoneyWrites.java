package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.TransactionCategory;
import com.vmargin.banking.util.MoneyValidation;
import com.vmargin.banking.service.exception.InsufficientBalanceException;
import com.vmargin.banking.service.exception.InvalidTransferException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class MoneyWrites {
    private MoneyWrites() {
    }

    static void claim(Connection connection, long ownerId, String reference, String kind) throws SQLException {
        if (reference == null || !UUID.fromString(reference).toString().equals(reference)) {
            throw new IllegalArgumentException("Invalid operation reference");
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO money_operations (reference, owner_id, kind) VALUES (?, ?, ?)")) {
            statement.setString(1, reference);
            statement.setLong(2, ownerId);
            statement.setString(3, kind);
            statement.executeUpdate();
        }
    }

    static BigDecimal lock(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT balance FROM users WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Account is no longer available");
                }
                return result.getBigDecimal(1);
            }
        }
    }

    static AccountState lockAccount(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT owner_id, balance, currency_code, status, is_primary
            FROM accounts WHERE id = ? FOR UPDATE
            """)) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Account is no longer available");
                }
                AccountState account = new AccountState(accountId, result.getLong("owner_id"),
                    result.getBigDecimal("balance"), result.getString("currency_code"),
                    result.getString("status"), result.getBoolean("is_primary"));
                if (!"ACTIVE".equals(account.status())) {
                    throw new IllegalArgumentException("This account is not available for money movements");
                }
                return account;
            }
        }
    }

    static void balance(Connection connection, long id, BigDecimal value) throws SQLException {
        if (value.signum() < 0 || value.compareTo(MoneyValidation.MAXIMUM) > 0) {
            throw new IllegalArgumentException("Account balance limit exceeded");
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET balance = ? WHERE id = ?")) {
            statement.setBigDecimal(1, value);
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Account update did not complete");
            }
        }
    }

    static void balanceAccount(Connection connection, AccountState account, BigDecimal value) throws SQLException {
        if (value.signum() < 0 || value.compareTo(MoneyValidation.MAXIMUM) > 0) {
            throw new IllegalArgumentException("Account balance limit exceeded");
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE accounts SET balance = ? WHERE id = ? AND status = 'ACTIVE'")) {
            statement.setBigDecimal(1, value);
            statement.setLong(2, account.id());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Account update did not complete");
            }
        }
        if (account.primary()) {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET balance = ? WHERE id = ?")) {
                statement.setBigDecimal(1, value);
                statement.setLong(2, account.ownerId());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Primary account compatibility balance update did not complete");
                }
            }
        }
    }

    static void ledger(Connection connection, long id, TransactionType type, BigDecimal amount,
                       String details, LocalDateTime occurredAt, String reference) throws SQLException {
        ledger(connection, id, primaryAccountId(connection, id), type, amount, details, occurredAt, reference);
    }

    static void ledger(Connection connection, long ownerId, long accountId, TransactionType type,
                       BigDecimal amount, String details, LocalDateTime occurredAt, String reference)
        throws SQLException {
        ledger(connection, ownerId, accountId, type, amount, details, occurredAt, reference,
            TransactionCategory.forType(type));
    }

    static void ledger(Connection connection, long ownerId, long accountId, TransactionType type,
                       BigDecimal amount, String details, LocalDateTime occurredAt, String reference,
                       TransactionCategory category) throws SQLException {
        MoneyValidation.details(details);
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO transactions (user_id, account_id, type, category, amount, details, occurred_at, reference)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setLong(1, ownerId);
            statement.setLong(2, accountId);
            statement.setString(3, type.name());
            statement.setString(4, category.name());
            statement.setBigDecimal(5, amount);
            statement.setString(6, details);
            statement.setTimestamp(7, Timestamp.valueOf(occurredAt));
            statement.setString(8, reference);
            statement.executeUpdate();
        }
    }

    static BigDecimal transfer(Connection connection, long senderId, long sourceAccountId,
                               long destinationAccountId, BigDecimal amount, LocalDateTime occurredAt,
                               String reference, String sourceDetails, String destinationDetails)
        throws SQLException {
        MoneyValidation.amount(amount);
        if (senderId < 1 || sourceAccountId < 1 || destinationAccountId < 1
            || sourceAccountId == destinationAccountId) {
            throw new InvalidTransferException("Choose a different source and recipient account");
        }
        Objects.requireNonNull(occurredAt, "Transfer time is required");
        claim(connection, senderId, reference, "TRANSFER");

        long firstId = Math.min(sourceAccountId, destinationAccountId);
        long secondId = Math.max(sourceAccountId, destinationAccountId);
        Map<Long, AccountState> locked = new HashMap<>();
        locked.put(firstId, lockAccount(connection, firstId));
        locked.put(secondId, lockAccount(connection, secondId));
        AccountState source = locked.get(sourceAccountId);
        AccountState destination = locked.get(destinationAccountId);
        if (source.ownerId() != senderId) {
            throw new InvalidTransferException("The source account is not available to this customer");
        }
        if (!source.currencyCode().equals(destination.currencyCode())) {
            throw new InvalidTransferException("Transfers between different currencies are unavailable in this demo");
        }
        BigDecimal nextSource = source.balance().subtract(amount);
        BigDecimal nextDestination = destination.balance().add(amount);
        if (nextSource.signum() < 0) {
            throw new InsufficientBalanceException("Insufficient balance for this transfer");
        }
        balanceAccount(connection, source, nextSource);
        balanceAccount(connection, destination, nextDestination);
        String sourceLastFour = accountNumber(connection, sourceAccountId).substring(8);
        String destinationLastFour = accountNumber(connection, destinationAccountId).substring(8);
        ledger(connection, source.ownerId(), sourceAccountId, TransactionType.TRANSFER_SENT,
            amount, sourceDetails.formatted(destinationLastFour), occurredAt, reference);
        ledger(connection, destination.ownerId(), destinationAccountId, TransactionType.TRANSFER_RECEIVED,
            amount, destinationDetails.formatted(sourceLastFour), occurredAt, reference);
        return nextSource;
    }

    private static String accountNumber(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT account_number FROM accounts WHERE id = ?")) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Account is no longer available");
                }
                return result.getString(1);
            }
        }
    }

    static long primaryAccountId(Connection connection, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM accounts WHERE owner_id = ? AND is_primary = TRUE")) {
            statement.setLong(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Primary account is no longer available");
                }
                return result.getLong(1);
            }
        }
    }

    record AccountState(long id, long ownerId, BigDecimal balance, String currencyCode,
                        String status, boolean primary) {
    }
}
