package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.exception.InvalidTransferException;
import com.vmargin.banking.service.exception.RecipientNotFoundException;
import com.vmargin.banking.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class JdbcTransferRepository implements TransferRepository {
    @Override
    public BigDecimal transfer(long senderId, String senderMobile, String recipientMobile,
                               BigDecimal amount, LocalDateTime occurredAt) throws SQLException {
        return transfer(senderId, senderMobile, recipientMobile, amount, occurredAt, UUID.randomUUID().toString());
    }

    @Override
    public BigDecimal transfer(long senderId, String senderMobile, String recipientMobile,
                               BigDecimal amount, LocalDateTime occurredAt, String reference) throws SQLException {
        if (recipientMobile == null || !recipientMobile.matches("09\\d{9}")) {
            throw new InvalidTransferException("Use a valid demo account recipient");
        }
        try (Connection connection = DatabaseConnection.open()) {
            long sourceId = MoneyWrites.primaryAccountId(connection, senderId);
            String destinationNumber = primaryAccountNumberForMobile(connection, recipientMobile);
            return transferToAccount(connection, senderId, sourceId, destinationNumber,
                amount, occurredAt, reference);
        }
    }

    @Override
    public BigDecimal transferToAccount(long senderId, long sourceAccountId, String recipientAccountNumber,
                                        BigDecimal amount,
                                        LocalDateTime occurredAt, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            return transferToAccount(connection, senderId, sourceAccountId,
                recipientAccountNumber, amount, occurredAt, reference);
        }
    }

    private BigDecimal transferToAccount(Connection connection, long senderId, long sourceAccountId,
                                         String recipientAccountNumber,
                                         BigDecimal amount, LocalDateTime occurredAt, String reference)
        throws SQLException {
        if (recipientAccountNumber == null || !recipientAccountNumber.matches("[0-9]{12}")) {
            throw new InvalidTransferException("Enter a 12-digit Cash - G demo account number");
        }
        long destinationAccountId = accountIdForNumber(connection, recipientAccountNumber);
        if (sourceAccountId == destinationAccountId) {
            throw new InvalidTransferException("Choose a different source and recipient account");
        }

        connection.setAutoCommit(false);
        try {
            BigDecimal updatedBalance = MoneyWrites.transfer(connection, senderId, sourceAccountId,
                destinationAccountId, amount, occurredAt, reference,
                "Transfer to account ending %s", "Transfer from account ending %s");
            connection.commit();
            return updatedBalance;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private String primaryAccountNumberForMobile(Connection connection, String mobile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT a.account_number FROM users u
            JOIN accounts a ON a.owner_id = u.id AND a.is_primary = TRUE
            WHERE u.mobile_number = ?
            """)) {
            statement.setString(1, mobile);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new RecipientNotFoundException("Recipient account was not found");
                }
                return result.getString(1);
            }
        }
    }

    private long accountIdForNumber(Connection connection, String accountNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM accounts WHERE account_number = ? AND status = 'ACTIVE'")) {
            statement.setString(1, accountNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new RecipientNotFoundException("Recipient account was not found");
                }
                return result.getLong(1);
            }
        }
    }

}
