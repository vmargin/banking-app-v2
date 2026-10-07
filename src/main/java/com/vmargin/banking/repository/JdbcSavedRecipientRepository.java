package com.vmargin.banking.repository;

import com.vmargin.banking.model.SavedRecipient;
import com.vmargin.banking.util.DatabaseConnection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcSavedRecipientRepository implements SavedRecipientRepository {
    @Override
    public List<SavedRecipient> findByOwner(long ownerId) throws SQLException {
        try (var connection = DatabaseConnection.open();
             var statement = connection.prepareStatement("""
                 SELECT id, recipient_account_number, label, created_at FROM saved_recipients
                 WHERE owner_id = ? ORDER BY label, id
                 """)) {
            statement.setLong(1, ownerId);
            try (var rows = statement.executeQuery()) {
                List<SavedRecipient> recipients = new ArrayList<>();
                while (rows.next()) {
                    Timestamp created = rows.getTimestamp("created_at");
                    recipients.add(new SavedRecipient(rows.getLong("id"),
                        rows.getString("recipient_account_number"),
                        rows.getString("label"), created.toLocalDateTime()));
                }
                return List.copyOf(recipients);
            }
        }
    }

    @Override
    public void save(long ownerId, String ownerMobile, String recipientAccountNumber, String label)
        throws SQLException {
        try (var connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                String recipientMobile;
                try (var target = connection.prepareStatement("""
                    SELECT u.mobile_number FROM users u
                    JOIN accounts a ON a.owner_id = u.id
                    WHERE a.account_number = ? AND a.status = 'ACTIVE'
                    """)) {
                    target.setString(1, recipientAccountNumber);
                    try (var rows = target.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("Saved recipient account does not exist", "02000");
                        }
                        recipientMobile = rows.getString(1);
                    }
                }
                try (var insert = connection.prepareStatement("""
                    INSERT INTO saved_recipients(owner_id, recipient_mobile, recipient_account_number, label)
                    SELECT ?, ?, ?, ? WHERE EXISTS (
                      SELECT 1 FROM users WHERE id = ? AND mobile_number = ?
                    )
                    """)) {
                    insert.setLong(1, ownerId);
                    insert.setString(2, recipientMobile);
                    insert.setString(3, recipientAccountNumber);
                    insert.setString(4, label);
                    insert.setLong(5, ownerId);
                    insert.setString(6, ownerMobile);
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("Saved recipient owner is unavailable", "02000");
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                if (isConstraintViolation(exception)) {
                    throw new DuplicateSavedRecipientException(exception);
                }
                throw exception;
            }
        }
    }

    @Override
    public boolean delete(long ownerId, long recipientId) throws SQLException {
        try (var connection = DatabaseConnection.open();
             var statement = connection.prepareStatement(
                 "DELETE FROM saved_recipients WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, ownerId);
            statement.setLong(2, recipientId);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean isConstraintViolation(SQLException exception) {
        String state = exception.getSQLState();
        return exception instanceof SQLIntegrityConstraintViolationException
            || (state != null && state.startsWith("23"));
    }

    public static final class DuplicateSavedRecipientException extends SQLException {
        private static final long serialVersionUID = 1L;

        DuplicateSavedRecipientException(SQLException cause) {
            super("Recipient is already saved or could not be saved", cause.getSQLState(), cause.getErrorCode(), cause);
        }
    }
}
