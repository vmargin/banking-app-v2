package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public class JdbcUserRepository implements UserRepository {

    private static final String SAVE_USER_SQL = """
        INSERT INTO users (mobile_number, pin, full_name, role, balance)
        VALUES (?, ?, ?, ?, ?)
        """;

    private static final String FIND_BY_MOBILE_SQL = """
        SELECT u.id, u.mobile_number, u.pin, u.full_name, u.role, a.balance,
               a.id AS account_id, a.account_number, a.account_name, a.account_type,
               a.currency_code, a.status, a.is_primary
        FROM users u JOIN accounts a ON a.owner_id = u.id AND a.is_primary = TRUE
        WHERE u.mobile_number = ?
        """;

    private static final String FIND_ALL_SQL = """
        SELECT u.id, u.mobile_number, u.pin, u.full_name, u.role, a.balance,
               a.id AS account_id, a.account_number, a.account_name, a.account_type,
               a.currency_code, a.status, a.is_primary
        FROM users u JOIN accounts a ON a.owner_id = u.id AND a.is_primary = TRUE
        ORDER BY u.full_name, u.id
        """;

    @Override
    public User save(User user) throws SQLException {
        Objects.requireNonNull(user, "User is required");

        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(SAVE_USER_SQL,
                Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, user.getMobileNumber());
                String pin = user.getPinForPersistence();
                statement.setString(2, com.vmargin.banking.util.PinHasher.isHash(pin)
                    ? pin : com.vmargin.banking.util.PinHasher.hash(pin));
                statement.setString(3, user.getFullName());
                statement.setString(4, user.getRole().name());
                statement.setBigDecimal(5, user.getBalance());
                statement.executeUpdate();
                try (ResultSet key = statement.getGeneratedKeys()) {
                    if (!key.next()) {
                        throw new SQLException("User ID was not generated");
                    }
                    JdbcAccountRepository.insertPrimary(connection, key.getLong(1), user.getFullName(),
                        user.getBalance());
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }

        return findByMobileNumber(user.getMobileNumber())
            .orElseThrow(() -> new IllegalStateException("Saved user cannot be found"));
    }

    @Override
    public Optional<User> findByMobileNumber(String mobileNumber) throws SQLException {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }

        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(FIND_BY_MOBILE_SQL)
        ) {
            statement.setString(1, mobileNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapUser(resultSet));
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return List.copyOf(users);
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String fullName = resultSet.getString("full_name");
        BankAccount bankAccount = new BankAccount(
            resultSet.getLong("account_id"), id,
            resultSet.getString("account_number"), fullName,
            resultSet.getString("account_name"), resultSet.getString("account_type"),
            resultSet.getString("currency_code"), resultSet.getString("status"),
            resultSet.getBoolean("is_primary"), resultSet.getBigDecimal("balance")
        );
        return new User(
            id,
            resultSet.getString("mobile_number"),
            resultSet.getString("pin"),
            fullName,
            bankAccount,
            UserRole.valueOf(resultSet.getString("role"))
        );
    }

    @Override
    public Optional<User> findById(long id) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_MOBILE_SQL.replace(
                 "WHERE u.mobile_number = ?", "WHERE u.id = ?"))) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
            }
        }
    }

    @Override
    public void updatePin(long id, String expectedPin, String newHash) throws SQLException {
        if (!com.vmargin.banking.util.PinHasher.isHash(newHash)) {
            throw new IllegalArgumentException("Replacement PIN must be a BCrypt hash");
        }
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE users SET pin = ? WHERE id = ? AND pin = ?")) {
            statement.setString(1, newHash);
            statement.setLong(2, id);
            statement.setString(3, expectedPin);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Credential changed; retry authentication");
            }
        }
    }
}
