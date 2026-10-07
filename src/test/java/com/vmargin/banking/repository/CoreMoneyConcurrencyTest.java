package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreMoneyConcurrencyTest {
    private JdbcUserRepository users;
    private User sender;
    private User recipient;

    @BeforeEach
    void setup() throws Exception {
        DatabaseConnection.useLocalH2("jdbc:h2:mem:core_rollback_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        try (Connection connection = DatabaseConnection.open()) {
            new JdbcSchemaMigrator().migrate(connection);
        }
        users = new JdbcUserRepository();
        sender = create("09990000011", "Sender", new BigDecimal("25.00"));
        recipient = create("09990000012", "Recipient", BigDecimal.ZERO);
        setBalance(recipient.getId(), MoneyValidation.MAXIMUM);
    }

    @AfterEach
    void cleanup() throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } finally {
            DatabaseConnection.clearLocalH2();
        }
    }

    private User create(String mobile, String name, BigDecimal balance) throws Exception {
        return users.save(new User(0, mobile, "1234", name,
            new BankAccount("PENDING", name, balance)));
    }

    private void setBalance(long userId, BigDecimal balance) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE accounts SET balance = ? WHERE owner_id = ? AND is_primary = TRUE")) {
            statement.setBigDecimal(1, balance);
            statement.setLong(2, userId);
            assertEquals(1, statement.executeUpdate());
        }
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET balance = ? WHERE id = ?")) {
            statement.setBigDecimal(1, balance);
            statement.setLong(2, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private BigDecimal balance(long userId) throws Exception {
        return users.findById(userId).orElseThrow().getBalance();
    }

    private long count(String table) throws SQLException {
        try (Connection connection = DatabaseConnection.open();
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    void recipientOverflowRollsBackSenderDebitAndOperationClaim() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new JdbcTransferRepository().transfer(
            sender.getId(), sender.getMobileNumber(), recipient.getMobileNumber(), new BigDecimal("1.00"),
            LocalDateTime.now(), UUID.randomUUID().toString()));

        assertEquals(new BigDecimal("25.00"), balance(sender.getId()));
        assertEquals(MoneyValidation.MAXIMUM, balance(recipient.getId()));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }
}
