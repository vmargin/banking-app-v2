package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class JdbcCashInRepository implements CashInRepository {

    private static final String UPDATE_BALANCE_SQL = """
        UPDATE users
        SET balance = balance + ?
        WHERE id = ?
        RETURNING balance
        """;

    private static final String INSERT_TRANSACTION_SQL = """
        INSERT INTO transactions (user_id, type, amount, details, occurred_at)
        VALUES (?, ?, ?, ?, ?)
        """;

    @Override
    public BigDecimal cashIn(
        long userId,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal updatedBalance = updateBalance(
                    connection,
                    userId,
                    amount
                );
                insertTransaction(connection, userId, amount, details, occurredAt);
                connection.commit();
                return updatedBalance;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private BigDecimal updateBalance(
        Connection connection,
        long userId,
        BigDecimal amount
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_BALANCE_SQL)) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("User account was not found");
                }
                return resultSet.getBigDecimal("balance");
            }
        }
    }

    private void insertTransaction(
        Connection connection,
        long userId,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRANSACTION_SQL)) {
            statement.setLong(1, userId);
            statement.setString(2, TransactionType.CASH_IN.name());
            statement.setBigDecimal(3, amount);
            statement.setString(4, details);
            statement.setTimestamp(5, Timestamp.valueOf(occurredAt));
            statement.executeUpdate();
        }
    }
}
