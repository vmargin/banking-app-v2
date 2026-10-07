package com.vmargin.banking.repository;

import com.vmargin.banking.model.MoneyRequest;
import com.vmargin.banking.model.MoneyRequestStatus;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcMoneyRequestRepository implements MoneyRequestRepository {
    private static final String SELECT_REQUEST = """
        SELECT r.id, r.reference, r.requester_user_id, requester.full_name AS requester_name,
               r.requester_account_id, requester_account.account_name AS requester_account_name,
               requester_account.account_number AS requester_account_number,
               r.payer_user_id, payer.full_name AS payer_name, r.amount,
               requester_account.currency_code, r.note, r.status, r.created_at, r.payment_reference
        FROM money_requests r
        JOIN users requester ON requester.id = r.requester_user_id
        JOIN accounts requester_account ON requester_account.id = r.requester_account_id
        JOIN users payer ON payer.id = r.payer_user_id
        """;

    @Override
    public List<MoneyRequest> findIncoming(long payerId) throws SQLException {
        return findMany(payerId, true);
    }

    @Override
    public List<MoneyRequest> findOutgoing(long requesterId) throws SQLException {
        return findMany(requesterId, false);
    }

    @Override
    public Optional<MoneyRequest> findPendingIncoming(long payerId, long requestId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_REQUEST
                 + " WHERE r.payer_user_id = ? AND r.id = ? AND r.status = 'PENDING'")) {
            statement.setLong(1, payerId);
            statement.setLong(2, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public MoneyRequest create(long requesterId, long requesterAccountId, String payerAccountNumber,
                               BigDecimal amount, String note, String reference, LocalDateTime createdAt)
        throws SQLException {
        MoneyValidation.amount(amount);
        if (requesterId < 1 || requesterAccountId < 1 || payerAccountNumber == null
            || !payerAccountNumber.matches("[0-9]{12}") || note == null || note.length() > 140
            || createdAt == null || !isUuid(reference)) {
            throw new IllegalArgumentException("Money request details are invalid.");
        }

        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                long payerAccountId = findAccountId(connection, payerAccountNumber);
                long firstId = Math.min(requesterAccountId, payerAccountId);
                long secondId = Math.max(requesterAccountId, payerAccountId);
                MoneyWrites.AccountState first = MoneyWrites.lockAccount(connection, firstId);
                MoneyWrites.AccountState second = MoneyWrites.lockAccount(connection, secondId);
                MoneyWrites.AccountState requesterAccount = requesterAccountId == firstId ? first : second;
                MoneyWrites.AccountState payerAccount = payerAccountId == firstId ? first : second;
                if (requesterAccount.ownerId() != requesterId) {
                    throw new IllegalArgumentException("Choose an active account on your profile.");
                }
                if (requesterAccount.ownerId() == payerAccount.ownerId()) {
                    throw new IllegalArgumentException("Choose another Nexa customer to request money from.");
                }
                if (!requesterAccount.currencyCode().equals(payerAccount.currencyCode())) {
                    throw new IllegalArgumentException("Requests must use the same currency on both accounts.");
                }
                long payerId = payerAccount.ownerId();
                long requestId;
                try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO money_requests(reference, requester_user_id, requester_account_id, payer_user_id,
                        amount, note, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, reference);
                    insert.setLong(2, requesterId);
                    insert.setLong(3, requesterAccountId);
                    insert.setLong(4, payerId);
                    insert.setBigDecimal(5, amount);
                    insert.setString(6, note);
                    insert.setTimestamp(7, Timestamp.valueOf(createdAt));
                    insert.setTimestamp(8, Timestamp.valueOf(createdAt));
                    insert.executeUpdate();
                    try (ResultSet key = insert.getGeneratedKeys()) {
                        if (!key.next()) {
                            throw new SQLException("Money request was not saved");
                        }
                        requestId = key.getLong(1);
                    }
                }
                MoneyRequest request = findById(connection, requestId)
                    .orElseThrow(() -> new SQLException("Saved money request could not be read"));
                connection.commit();
                return request;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public BigDecimal pay(long payerId, long requestId, long sourceAccountId, String paymentReference,
                          LocalDateTime occurredAt) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                long requesterAccountId;
                BigDecimal amount;
                try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT requester_account_id, amount, status FROM money_requests
                    WHERE id = ? AND payer_user_id = ? FOR UPDATE
                    """)) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, payerId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Incoming request was not found.");
                        }
                        if (!"PENDING".equals(result.getString("status"))) {
                            throw new IllegalArgumentException("This money request is no longer pending.");
                        }
                        requesterAccountId = result.getLong("requester_account_id");
                        amount = result.getBigDecimal("amount");
                    }
                }
                BigDecimal sourceBalance = MoneyWrites.transfer(connection, payerId, sourceAccountId,
                    requesterAccountId, amount, occurredAt, paymentReference,
                    "Payment request to account ending %s", "Requested payment from account ending %s");
                try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE money_requests SET status = 'PAID', payment_reference = ?, updated_at = ?
                    WHERE id = ? AND payer_user_id = ? AND status = 'PENDING'
                    """)) {
                    update.setString(1, paymentReference);
                    update.setTimestamp(2, Timestamp.valueOf(occurredAt));
                    update.setLong(3, requestId);
                    update.setLong(4, payerId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Money request status did not change");
                    }
                }
                connection.commit();
                return sourceBalance;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean decline(long payerId, long requestId, LocalDateTime updatedAt) throws SQLException {
        return resolve(payerId, requestId, "DECLINED", updatedAt, true);
    }

    @Override
    public boolean cancel(long requesterId, long requestId, LocalDateTime updatedAt) throws SQLException {
        return resolve(requesterId, requestId, "CANCELED", updatedAt, false);
    }

    private List<MoneyRequest> findMany(long ownerId, boolean incoming) throws SQLException {
        String ownerColumn = incoming ? "r.payer_user_id" : "r.requester_user_id";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_REQUEST + " WHERE " + ownerColumn
                 + " = ? ORDER BY r.created_at DESC, r.id DESC")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                List<MoneyRequest> requests = new ArrayList<>();
                while (rows.next()) {
                    requests.add(map(rows));
                }
                return List.copyOf(requests);
            }
        }
    }

    private boolean resolve(long ownerId, long requestId, String status, LocalDateTime updatedAt,
                            boolean incoming) throws SQLException {
        String ownerColumn = incoming ? "payer_user_id" : "requester_user_id";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement("UPDATE money_requests SET status = ?, "
                 + "updated_at = ? WHERE id = ? AND " + ownerColumn + " = ? AND status = 'PENDING'")) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, requestId);
            statement.setLong(4, ownerId);
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<MoneyRequest> findById(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_REQUEST + " WHERE r.id = ?")) {
            statement.setLong(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private long findAccountId(Connection connection, String accountNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM accounts WHERE account_number = ? AND status = 'ACTIVE'")) {
            statement.setString(1, accountNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("The recipient account could not be found.");
                }
                return result.getLong(1);
            }
        }
    }

    private MoneyRequest map(ResultSet rows) throws SQLException {
        String accountNumber = rows.getString("requester_account_number");
        Timestamp created = rows.getTimestamp("created_at");
        return new MoneyRequest(rows.getLong("id"), rows.getString("reference"),
            rows.getLong("requester_user_id"), rows.getString("requester_name"),
            rows.getLong("requester_account_id"), rows.getString("requester_account_name"),
            accountNumber.substring(8), rows.getLong("payer_user_id"), rows.getString("payer_name"),
            rows.getBigDecimal("amount"), rows.getString("currency_code"), rows.getString("note"),
            MoneyRequestStatus.valueOf(rows.getString("status")), created.toLocalDateTime(),
            rows.getString("payment_reference"));
    }

    private boolean isUuid(String reference) {
        if (reference == null) {
            return false;
        }
        try {
            return UUID.fromString(reference).toString().equals(reference);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
