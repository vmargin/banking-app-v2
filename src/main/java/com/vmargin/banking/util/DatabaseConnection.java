package com.vmargin.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String DEFAULT_URL =
        "jdbc:postgresql://localhost:5432/banking_app_v2";

    private DatabaseConnection() {
    }

    public static Connection open() throws SQLException {
        String url = getEnvironmentValue("BANKING_DB_URL", DEFAULT_URL);
        String username = getRequiredEnvironmentValue("BANKING_DB_USER");
        String password = getRequiredEnvironmentValue("BANKING_DB_PASSWORD");

        return DriverManager.getConnection(url, username, password);
    }

    private static String getEnvironmentValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String getRequiredEnvironmentValue(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required environment variable is missing: " + name
            );
        }

        return value;
    }
}
