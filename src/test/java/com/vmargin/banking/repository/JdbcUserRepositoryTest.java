package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class JdbcUserRepositoryTest {

    @Test
    void savesAndFindsUserByMobileNumber() throws SQLException {
        Assumptions.assumeTrue(
            System.getenv("BANKING_DB_USER") != null
                && System.getenv("BANKING_DB_PASSWORD") != null,
            "Database credentials are required"
        );

        BankAccount account = new BankAccount(
            "ACC-TEST",
            "Test User",
            new BigDecimal("2500.00")
        );

        User user = new User(
            1L,
            "09990000001",
            "1234",
            "Test User",
            account
        );

        UserRepository repository = new JdbcUserRepository();
        repository.save(user);

        Optional<User> result = repository.findByMobileNumber("09990000001");

        assertTrue(result.isPresent());
        assertEquals("Test User", result.get().getFullName());
        assertEquals(new BigDecimal("2500.00"), result.get().getBalance());
    }
}
