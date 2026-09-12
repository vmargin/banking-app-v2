package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.exception.InsufficientBalanceException;
import com.vmargin.banking.service.exception.RecipientNotFoundException;
import com.vmargin.banking.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class JdbcTransferRepository implements TransferRepository {

    private static final String FIND_RECIPIENT_SQL = """
        SELECT id
        FROM users
        WHERE mobile_number = ?
        FOR UPDATE
        """;

    private static final String DEBIT_SENDER_SQL = """
        UPDATE users
        SET balance = balance - ?
        WHERE id = ? AND balance >= ?
        RETURNING balance
        """;

    private static final String CREDIT_RECIPIENT_SQL = """
        UPDATE users
        SET balance = balance + ?
        WHERE id = ?
        """;

    private static final String INSERT_TRANSACTION_SQL = """
        INSERT INTO transactions (user_id, type, amount, details, occurred_at)
        VALUES (?, ?, ?, ?, ?)
        """;

    @Override
    public BigDecimal transfer(
        long senderId,
        String senderMobileNumber,
        String recipientMobileNumber,
        BigDecimal amount,
        LocalDateTime occurredAt
    ) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                long recipientId = findRecipientId(connection, recipientMobileNumber);
                BigDecimal senderBalance = debitSender(connection, senderId, amount);
                creditRecipient(connection, recipientId, amount);
                insertTransaction(
                    connection,
                    senderId,
                    TransactionType.TRANSFER_SENT,
                    amount,
                    "Transfer to " + recipientMobileNumber,
                    occurredAt
                );
                insertTransaction(
                    connection,
                    recipientId,
                    TransactionType.TRANSFER_RECEIVED,
                    amount,
                    "Transfer from " + senderMobileNumber,
                    occurredAt
                );
                connection.commit();
                return senderBalance;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private long findRecipientId(Connection connection, String mobileNumber)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_RECIPIENT_SQL)) {
            statement.setString(1, mobileNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new RecipientNotFoundException("Recipient account was not found");
                }
                return resultSet.getLong("id");
            }
        }
    }

    private BigDecimal debitSender(Connection connection, long senderId, BigDecimal amount)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEBIT_SENDER_SQL)) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, senderId);
            statement.setBigDecimal(3, amount);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new InsufficientBalanceException("Insufficient balance for this transfer");
                }
                return resultSet.getBigDecimal("balance");
            }
        }
    }

    private void creditRecipient(Connection connection, long recipientId, BigDecimal amount)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREDIT_RECIPIENT_SQL)) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, recipientId);
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
        Connection connection,
        long userId,
        TransactionType type,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRANSACTION_SQL)) {
            statement.setLong(1, userId);
            statement.setString(2, type.name());
            statement.setBigDecimal(3, amount);
            statement.setString(4, details);
            statement.setTimestamp(5, Timestamp.valueOf(occurredAt));
            statement.executeUpdate();
        }
    }
}
