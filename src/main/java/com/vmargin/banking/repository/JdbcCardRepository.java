package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankCard;
import com.vmargin.banking.model.CardReplacementReason;
import com.vmargin.banking.model.CardReplacementRequest;
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
import java.util.UUID;

public class JdbcCardRepository implements CardRepository {
    private static final String SELECT_CARD = """
        SELECT c.id, c.owner_id, c.account_id, a.account_name, c.card_name, c.brand,
               c.card_type, c.last_four, c.status, c.daily_limit, c.online_enabled, c.created_at
        FROM cards c JOIN accounts a ON a.id = c.account_id
        """;
    private static final String SELECT_REPLACEMENT = """
        SELECT id, card_id, card_name, last_four, reason, status, reference, created_at
        FROM card_replacement_requests
        """;

    @Override
    public List<BankCard> findByOwner(long ownerId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_CARD
                 + " WHERE c.owner_id = ? ORDER BY c.created_at DESC, c.id DESC")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                List<BankCard> cards = new ArrayList<>();
                while (rows.next()) {
                    cards.add(map(rows));
                }
                return List.copyOf(cards);
            }
        }
    }

    @Override
    public boolean setFrozen(long ownerId, long cardId, boolean frozen) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM cards WHERE owner_id = ? AND id = ? FOR UPDATE")) {
                    lock.setLong(1, ownerId);
                    lock.setLong(2, cardId);
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            connection.commit();
                            return false;
                        }
                    }
                }
                if (!frozen && hasOpenReplacement(connection, ownerId, cardId)) {
                    connection.commit();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cards SET status = ? WHERE owner_id = ? AND id = ?")) {
                    statement.setString(1, frozen ? "FROZEN" : "ACTIVE");
                    statement.setLong(2, ownerId);
                    statement.setLong(3, cardId);
                    boolean updated = statement.executeUpdate() == 1;
                    connection.commit();
                    return updated;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean setOnlineEnabled(long ownerId, long cardId, boolean enabled) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE cards SET online_enabled = ? WHERE owner_id = ? AND id = ?")) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, ownerId);
            statement.setLong(3, cardId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean setDailyLimit(long ownerId, long cardId, BigDecimal dailyLimit) throws SQLException {
        MoneyValidation.amount(dailyLimit);
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE cards SET daily_limit = ? WHERE owner_id = ? AND id = ?")) {
            statement.setBigDecimal(1, dailyLimit);
            statement.setLong(2, ownerId);
            statement.setLong(3, cardId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public BankCard createDemoIfMissing(long ownerId, long accountId, String accountName,
                                       String cardName, String brand, String lastFour,
                                       BigDecimal dailyLimit) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            try (PreparedStatement existing = connection.prepareStatement(SELECT_CARD
                + " WHERE c.owner_id = ? AND c.card_name = ?")) {
                existing.setLong(1, ownerId);
                existing.setString(2, cardName);
                try (ResultSet rows = existing.executeQuery()) {
                    if (rows.next()) {
                        return map(rows);
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO cards(owner_id, account_id, card_name, brand, card_type, last_four,
                    status, daily_limit)
                SELECT ?, a.id, ?, ?, 'DEBIT', ?, 'ACTIVE', ?
                FROM accounts a
                WHERE a.owner_id = ? AND a.id = ? AND a.status = 'ACTIVE' AND a.currency_code = 'PHP'
                """, Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, ownerId);
                insert.setString(2, cardName);
                insert.setString(3, brand);
                insert.setString(4, lastFour);
                insert.setBigDecimal(5, dailyLimit);
                insert.setLong(6, ownerId);
                insert.setLong(7, accountId);
                if (insert.executeUpdate() != 1) {
                    throw new SQLException("Choose an active PHP account owned by this customer", "02000");
                }
                try (ResultSet key = insert.getGeneratedKeys()) {
                    if (!key.next()) {
                        throw new SQLException("Demo card ID was not generated");
                    }
                    try (PreparedStatement select = connection.prepareStatement(SELECT_CARD
                        + " WHERE c.owner_id = ? AND c.id = ?")) {
                        select.setLong(1, ownerId);
                        select.setLong(2, key.getLong(1));
                        try (ResultSet rows = select.executeQuery()) {
                            if (!rows.next()) {
                                throw new SQLException("Demo card was not saved");
                            }
                            return map(rows);
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<CardReplacementRequest> findReplacementRequests(long ownerId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_REPLACEMENT
                 + " WHERE owner_id = ? ORDER BY created_at DESC, id DESC")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                List<CardReplacementRequest> requests = new ArrayList<>();
                while (rows.next()) {
                    requests.add(mapReplacement(rows));
                }
                return List.copyOf(requests);
            }
        }
    }

    @Override
    public CardReplacementRequest requestReplacement(long ownerId, long cardId,
                                                      CardReplacementReason reason, String reference,
                                                      LocalDateTime createdAt) throws SQLException {
        if (ownerId < 1 || cardId < 1 || reason == null || !isUuid(reference) || createdAt == null) {
            throw new IllegalArgumentException("Replacement request details are invalid.");
        }
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement card = connection.prepareStatement(
                    "SELECT id FROM cards WHERE owner_id = ? AND id = ? FOR UPDATE")) {
                    card.setLong(1, ownerId);
                    card.setLong(2, cardId);
                    try (ResultSet rows = card.executeQuery()) {
                        if (!rows.next()) {
                            throw new IllegalArgumentException("Practice card was not found on your profile.");
                        }
                    }
                }
                CardReplacementRequest existing = findOpenReplacement(connection, ownerId, cardId).orElse(null);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                try (PreparedStatement freeze = connection.prepareStatement(
                    "UPDATE cards SET status = 'FROZEN' WHERE owner_id = ? AND id = ?")) {
                    freeze.setLong(1, ownerId);
                    freeze.setLong(2, cardId);
                    if (freeze.executeUpdate() != 1) {
                        throw new SQLException("Practice card could not be frozen");
                    }
                }
                long requestId;
                try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO card_replacement_requests(owner_id, card_id, card_name, last_four, reason,
                        status, reference, created_at, updated_at)
                    SELECT c.owner_id, c.id, c.card_name, c.last_four, ?, 'OPEN', ?, ?, ?
                    FROM cards c WHERE c.owner_id = ? AND c.id = ?
                    """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, reason.name());
                    insert.setString(2, reference);
                    insert.setTimestamp(3, Timestamp.valueOf(createdAt));
                    insert.setTimestamp(4, Timestamp.valueOf(createdAt));
                    insert.setLong(5, ownerId);
                    insert.setLong(6, cardId);
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("Card replacement request was not saved");
                    }
                    try (ResultSet key = insert.getGeneratedKeys()) {
                        if (!key.next()) {
                            throw new SQLException("Card replacement request ID was not generated");
                        }
                        requestId = key.getLong(1);
                    }
                }
                CardReplacementRequest request = findReplacementById(connection, ownerId, requestId)
                    .orElseThrow(() -> new SQLException("Saved replacement request could not be read"));
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
    public boolean cancelReplacementRequest(long ownerId, long cardId, LocalDateTime updatedAt)
        throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM cards WHERE owner_id = ? AND id = ? FOR UPDATE")) {
                    lock.setLong(1, ownerId);
                    lock.setLong(2, cardId);
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            connection.commit();
                            return false;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE card_replacement_requests SET status = 'CANCELED', updated_at = ?
                    WHERE owner_id = ? AND card_id = ? AND status = 'OPEN'
                    """)) {
                    update.setTimestamp(1, Timestamp.valueOf(updatedAt));
                    update.setLong(2, ownerId);
                    update.setLong(3, cardId);
                    boolean updated = update.executeUpdate() == 1;
                    connection.commit();
                    return updated;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean hasOpenReplacement(Connection connection, long ownerId, long cardId) throws SQLException {
        return findOpenReplacement(connection, ownerId, cardId).isPresent();
    }

    private java.util.Optional<CardReplacementRequest> findOpenReplacement(Connection connection, long ownerId,
                                                                            long cardId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_REPLACEMENT
            + " WHERE owner_id = ? AND card_id = ? AND status = 'OPEN' FOR UPDATE")) {
            statement.setLong(1, ownerId);
            statement.setLong(2, cardId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? java.util.Optional.of(mapReplacement(rows)) : java.util.Optional.empty();
            }
        }
    }

    private java.util.Optional<CardReplacementRequest> findReplacementById(Connection connection, long ownerId,
                                                                            long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_REPLACEMENT
            + " WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, ownerId);
            statement.setLong(2, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? java.util.Optional.of(mapReplacement(rows)) : java.util.Optional.empty();
            }
        }
    }

    private CardReplacementRequest mapReplacement(ResultSet rows) throws SQLException {
        Timestamp created = rows.getTimestamp("created_at");
        return new CardReplacementRequest(rows.getLong("id"), rows.getLong("card_id"),
            rows.getString("card_name"), rows.getString("last_four"),
            CardReplacementReason.valueOf(rows.getString("reason")), rows.getString("status"),
            rows.getString("reference"), created.toLocalDateTime());
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

    private BankCard map(ResultSet rows) throws SQLException {
        return new BankCard(rows.getLong("id"), rows.getLong("owner_id"), rows.getLong("account_id"),
            rows.getString("account_name"), rows.getString("card_name"), rows.getString("brand"),
            rows.getString("card_type"), rows.getString("last_four"), rows.getString("status"),
            rows.getBigDecimal("daily_limit"), rows.getBoolean("online_enabled"),
            rows.getTimestamp("created_at").toLocalDateTime());
    }
}
