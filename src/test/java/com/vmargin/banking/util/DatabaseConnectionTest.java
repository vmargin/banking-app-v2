package com.vmargin.banking.util;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DatabaseConnectionTest {

    @Test
    void opensPostgresConnection() throws SQLException {
        String username = System.getenv("BANKING_DB_USER");
        String password = System.getenv("BANKING_DB_PASSWORD");
        Assumptions.assumeTrue(
            username != null && !username.isBlank()
                && password != null && !password.isBlank(),
            "Set BANKING_DB_USER and BANKING_DB_PASSWORD for the integration test"
        );

        try (Connection connection = DatabaseConnection.open()) {
            assertFalse(connection.isClosed());
        }
    }
}
