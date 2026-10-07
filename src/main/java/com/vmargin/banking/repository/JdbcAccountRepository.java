package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.util.AccountNumberGenerator;
import com.vmargin.banking.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAccountRepository implements AccountRepository {
    private static final String SELECT_COLUMNS = """
        SELECT a.id, a.owner_id, a.account_number, u.full_name, a.account_name, a.account_type,
               a.currency_code, a.balance, a.status, a.is_primary
        FROM accounts a JOIN users u ON u.id = a.owner_id
        """;

    @Override
    public List<BankAccount> findByOwner(long ownerId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS
                 + " WHERE a.owner_id = ? ORDER BY a.is_primary DESC, a.created_at, a.id")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                List<BankAccount> accounts = new ArrayList<>();
                while (rows.next()) {
                    accounts.add(map(rows));
                }
                return List.copyOf(accounts);
            }
        }
    }

    @Override
    public Optional<BankAccount> findByIdAndOwner(long ownerId, long accountId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS
                 + " WHERE a.owner_id = ? AND a.id = ?")) {
            statement.setLong(1, ownerId);
            statement.setLong(2, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<BankAccount> findByAccountNumber(String accountNumber) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS
                 + " WHERE a.account_number = ?")) {
            statement.setString(1, accountNumber);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public long findPrimaryId(long ownerId) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id FROM accounts WHERE owner_id = ? AND is_primary = TRUE")) {
            statement.setLong(1, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Primary account is missing for this customer");
                }
                return rows.getLong(1);
            }
        }
    }

    @Override
    public BankAccount create(long ownerId, String holderName, String accountName, String accountType,
                              String currencyCode, BigDecimal openingBalance) throws SQLException {
        validateOpeningBalance(openingBalance);
        try (Connection connection = DatabaseConnection.open()) {
            return insertWithUniqueNumber(connection, ownerId, holderName, accountName, accountType,
                currencyCode, openingBalance, false);
        }
    }

    @Override
    public BankAccount openDemoAccountIfMissing(long ownerId, String holderName, String accountName,
                                                String accountType, String currencyCode,
                                                BigDecimal openingBalance) throws SQLException {
        validateOpeningBalance(openingBalance);
        try (Connection connection = DatabaseConnection.open()) {
            try (PreparedStatement existing = connection.prepareStatement(SELECT_COLUMNS
                + " WHERE a.owner_id = ? AND a.account_name = ?")) {
                existing.setLong(1, ownerId);
                existing.setString(2, accountName);
                try (ResultSet rows = existing.executeQuery()) {
                    if (rows.next()) {
                        return map(rows);
                    }
                }
            }
            return insertWithUniqueNumber(connection, ownerId, holderName, accountName, accountType,
                currencyCode, openingBalance, false);
        }
    }

    public static BankAccount insertPrimary(Connection connection, long ownerId, String holderName,
                                            BigDecimal openingBalance) throws SQLException {
        return insertWithUniqueNumber(connection, ownerId, holderName, "Everyday account", "CHECKING",
            "PHP", openingBalance, true);
    }

    private static BankAccount insertWithUniqueNumber(Connection connection, long ownerId, String holderName,
                                                      String accountName, String accountType,
                                                      String currencyCode, BigDecimal openingBalance,
                                                      boolean primary) throws SQLException {
        for (int attempt = 0; attempt < 4; attempt++) {
            String accountNumber = AccountNumberGenerator.next();
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts(owner_id, account_number, account_name, account_type,
                    currency_code, balance, status, is_primary)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, ownerId);
                statement.setString(2, accountNumber);
                statement.setString(3, accountName);
                statement.setString(4, accountType);
                statement.setString(5, currencyCode);
                statement.setBigDecimal(6, openingBalance);
                statement.setBoolean(7, primary);
                statement.executeUpdate();
                try (ResultSet generated = statement.getGeneratedKeys()) {
                    if (!generated.next()) {
                        throw new SQLException("Account ID was not generated");
                    }
                    return new BankAccount(generated.getLong(1), ownerId, accountNumber, holderName,
                        accountName, accountType, currencyCode, "ACTIVE", primary, openingBalance);
                }
            } catch (SQLException exception) {
                if (!"23505".equals(exception.getSQLState()) || attempt == 3) {
                    throw exception;
                }
            }
        }
        throw new SQLException("Could not allocate a unique demo account number");
    }

    private static BankAccount map(ResultSet rows) throws SQLException {
        return new BankAccount(rows.getLong("id"), rows.getLong("owner_id"),
            rows.getString("account_number"), rows.getString("full_name"), rows.getString("account_name"),
            rows.getString("account_type"), rows.getString("currency_code"), rows.getString("status"),
            rows.getBoolean("is_primary"), rows.getBigDecimal("balance"));
    }

    private static void validateOpeningBalance(BigDecimal openingBalance) {
        if (openingBalance == null || openingBalance.signum() < 0 || openingBalance.scale() > 2) {
            throw new IllegalArgumentException("Opening balance must be nonnegative with at most two decimals.");
        }
    }
}
