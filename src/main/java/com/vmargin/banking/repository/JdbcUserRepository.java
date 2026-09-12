package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public class JdbcUserRepository implements UserRepository {

    private static final String SAVE_USER_SQL = """
        INSERT INTO users (mobile_number, pin, full_name, role, balance)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (mobile_number)
        DO UPDATE SET
            pin = EXCLUDED.pin,
            full_name = EXCLUDED.full_name,
            role = EXCLUDED.role,
            balance = EXCLUDED.balance
        """;

    private static final String FIND_BY_MOBILE_SQL = """
        SELECT id, mobile_number, pin, full_name, role, balance
        FROM users
        WHERE mobile_number = ?
        """;

    private static final String FIND_ALL_SQL = """
        SELECT id, mobile_number, pin, full_name, role, balance
        FROM users
        ORDER BY full_name, id
        """;

    @Override
    public User save(User user) throws SQLException {
        Objects.requireNonNull(user, "User is required");

        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(SAVE_USER_SQL)
        ) {
            statement.setString(1, user.getMobileNumber());
            statement.setString(2, user.getPinForPersistence());
            statement.setString(3, user.getFullName());
            statement.setString(4, user.getRole().name());
            statement.setBigDecimal(5, user.getBalance());
            statement.executeUpdate();
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
            "ACC-" + id,
            fullName,
            resultSet.getBigDecimal("balance")
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
}
