package com.vmargin.banking.repository;

import com.vmargin.banking.model.SavingsGoal;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.exception.InsufficientBalanceException;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcSavingsGoalRepository implements SavingsGoalRepository {
    @Override
    public List<SavingsGoal> findByOwner(long ownerId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, owner_id, name, target_amount, saved_amount, created_at "
                     + "FROM savings_goals WHERE owner_id = ? ORDER BY created_at DESC, id DESC")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                List<SavingsGoal> goals = new ArrayList<>();
                while (rows.next()) {
                    goals.add(map(rows));
                }
                return List.copyOf(goals);
            }
        }
    }

    @Override
    public SavingsGoal findByOwnerAndId(long ownerId, long goalId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, owner_id, name, target_amount, saved_amount, created_at "
                     + "FROM savings_goals WHERE owner_id = ? AND id = ?")) {
            statement.setLong(1, ownerId);
            statement.setLong(2, goalId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    @Override
    public SavingsGoal create(long ownerId, String name, BigDecimal targetAmount) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO savings_goals(owner_id, name, target_amount) VALUES (?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, ownerId);
            statement.setString(2, name);
            statement.setBigDecimal(3, targetAmount);
            statement.executeUpdate();
            try (ResultSet key = statement.getGeneratedKeys()) {
                if (!key.next()) {
                    throw new SQLException("Savings goal was not created");
                }
                return findByOwnerAndId(ownerId, key.getLong(1));
            }
        }
    }

    @Override
    public SavingsGoal createDemoIfMissing(long ownerId, String name, BigDecimal targetAmount,
                                           BigDecimal initialSavedAmount, LocalDateTime occurredAt,
                                           String operationReference) throws SQLException {
        MoneyValidation.amount(targetAmount);
        if (initialSavedAmount == null || initialSavedAmount.signum() < 0 || initialSavedAmount.scale() > 2
            || initialSavedAmount.compareTo(targetAmount) > 0 || name == null || name.isBlank()
            || name.length() > 40 || occurredAt == null) {
            throw new IllegalArgumentException("Synthetic savings goal values are invalid.");
        }
        long goalId;
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                long accountId = MoneyWrites.primaryAccountId(connection, ownerId);
                MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
                if (account.ownerId() != ownerId || !"PHP".equals(account.currencyCode())) {
                    throw new IllegalArgumentException("Demo savings goals require the customer's PHP account.");
                }
                SavingsGoal existing = findByOwnerAndName(connection, ownerId, name);
                if (existing != null) {
                    goalId = existing.id();
                    connection.commit();
                } else {
                    if (initialSavedAmount.compareTo(account.balance()) > 0) {
                        throw new InsufficientBalanceException(
                            "Initial demo goal reserve exceeds the account balance.");
                    }
                    if (initialSavedAmount.signum() > 0) {
                        MoneyWrites.claim(connection, ownerId, operationReference, "SAVINGS_CONTRIBUTION");
                    }
                    try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO savings_goals(owner_id, name, target_amount, saved_amount)
                        VALUES (?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                        insert.setLong(1, ownerId);
                        insert.setString(2, name);
                        insert.setBigDecimal(3, targetAmount);
                        insert.setBigDecimal(4, initialSavedAmount);
                        insert.executeUpdate();
                        try (ResultSet key = insert.getGeneratedKeys()) {
                            if (!key.next()) {
                                throw new SQLException("Synthetic savings goal was not created");
                            }
                            goalId = key.getLong(1);
                        }
                    }
                    if (initialSavedAmount.signum() > 0) {
                        MoneyWrites.balanceAccount(connection, account,
                            account.balance().subtract(initialSavedAmount));
                        MoneyWrites.ledger(connection, ownerId, accountId, TransactionType.SAVINGS_CONTRIBUTION,
                            initialSavedAmount, "Savings contribution: " + name, occurredAt, operationReference);
                    }
                    connection.commit();
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return findByOwnerAndId(ownerId, goalId);
    }

    @Override
    public void move(long ownerId, long goalId, BigDecimal amount, boolean contribution,
                     LocalDateTime occurredAt, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            move(connection, ownerId, MoneyWrites.primaryAccountId(connection, ownerId), goalId,
                amount, contribution, occurredAt, reference);
        }
    }

    @Override
    public void moveFromAccount(long ownerId, long accountId, long goalId, BigDecimal amount,
                                boolean contribution, LocalDateTime occurredAt, String reference)
        throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            move(connection, ownerId, accountId, goalId, amount, contribution, occurredAt, reference);
        }
    }

    private void move(Connection connection, long ownerId, long accountId, long goalId, BigDecimal amount,
                      boolean contribution, LocalDateTime occurredAt, String reference) throws SQLException {
        MoneyValidation.amount(amount);
        connection.setAutoCommit(false);
        try {
            MoneyWrites.claim(connection, ownerId, reference,
                contribution ? "SAVINGS_CONTRIBUTION" : "SAVINGS_WITHDRAWAL");
            MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
            if (account.ownerId() != ownerId || !"PHP".equals(account.currencyCode())) {
                throw new IllegalArgumentException("Choose an eligible PHP account for this savings move.");
            }
            LockedGoal goal = lockGoal(connection, ownerId, goalId);
            BigDecimal saved = goal.savedAmount();
            BigDecimal nextBalance = contribution ? account.balance().subtract(amount) : account.balance().add(amount);
            BigDecimal nextSaved = contribution ? saved.add(amount) : saved.subtract(amount);
            if (contribution && account.balance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("Insufficient available balance for this contribution.");
            }
            if (!contribution && saved.compareTo(amount) < 0) {
                throw new InsufficientBalanceException("This goal does not have enough saved funds to withdraw.");
            }
            if (nextSaved.compareTo(MoneyValidation.MAXIMUM) > 0) {
                throw new IllegalArgumentException("Savings goal balance limit exceeded.");
            }
            MoneyWrites.balanceAccount(connection, account, nextBalance);
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE savings_goals SET saved_amount = ? WHERE id = ? AND owner_id = ?")) {
                update.setBigDecimal(1, nextSaved);
                update.setLong(2, goalId);
                update.setLong(3, ownerId);
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Savings goal update did not complete");
                }
            }
            MoneyWrites.ledger(connection, ownerId, accountId,
                contribution ? TransactionType.SAVINGS_CONTRIBUTION : TransactionType.SAVINGS_WITHDRAWAL,
                amount, (contribution ? "Savings contribution: " : "Savings withdrawal: ") + goal.name(),
                occurredAt, reference);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private LockedGoal lockGoal(Connection connection, long ownerId, long goalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT name, saved_amount FROM savings_goals WHERE id = ? AND owner_id = ? FOR UPDATE")) {
            statement.setLong(1, goalId);
            statement.setLong(2, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Savings goal was not found on your account.");
                }
                return new LockedGoal(rows.getString(1), rows.getBigDecimal(2));
            }
        }
    }

    private SavingsGoal map(ResultSet rows) throws SQLException {
        return new SavingsGoal(rows.getLong("id"), rows.getLong("owner_id"), rows.getString("name"),
            rows.getBigDecimal("target_amount"), rows.getBigDecimal("saved_amount"),
            rows.getTimestamp("created_at").toLocalDateTime());
    }

    private SavingsGoal findByOwnerAndName(Connection connection, long ownerId, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
            SELECT id, owner_id, name, target_amount, saved_amount, created_at
            FROM savings_goals WHERE owner_id = ? AND name = ?
            """)) {
            query.setLong(1, ownerId);
            query.setString(2, name);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    private record LockedGoal(String name, BigDecimal savedAmount) {
    }
}
