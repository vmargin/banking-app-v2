package com.vmargin.banking.util;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String DEFAULT_URL =
        "jdbc:postgresql://localhost:5432/banking_app_v2";
    private static volatile String localH2Url;

    private DatabaseConnection() {
    }

    public static Connection open() throws SQLException {
        String demoUrl = localH2Url;
        if (demoUrl != null) {
            return DriverManager.getConnection(demoUrl, "sa", "");
        }
        String url = getEnvironmentValue("BANKING_DB_URL", DEFAULT_URL);
        return open(url);
    }

    /** Opens only the local v2 PostgreSQL target for explicit schema maintenance commands. */
    public static Connection openLocalV2Postgres() throws SQLException {
        String url = getEnvironmentValue("BANKING_DB_URL", DEFAULT_URL);
        if (!isLocalV2PostgresUrl(url)) {
            throw new IllegalStateException(
                "Database maintenance requires jdbc:postgresql on localhost for banking_app_v2"
            );
        }
        return open(url);
    }

    static boolean isLocalV2PostgresUrl(String url) {
        if (url == null || !url.startsWith("jdbc:")) {
            return false;
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String host = uri.getHost();
            return "postgresql".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))
                && "/banking_app_v2".equals(uri.getRawPath())
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isLocalH2Url(String url) {
        if (url == null || !url.startsWith("jdbc:h2:")) {
            return false;
        }
        int optionsStart = url.indexOf(';');
        String database = optionsStart < 0 ? url : url.substring(0, optionsStart);
        if (database.startsWith("jdbc:h2:mem:")) {
            String name = database.substring("jdbc:h2:mem:".length());
            if (!name.matches("[A-Za-z0-9_-]+")) {
                return false;
            }
        } else if (database.startsWith("jdbc:h2:file:")) {
            try {
                Path root = Path.of("tmp").toAbsolutePath().normalize();
                Path file = Path.of(database.substring("jdbc:h2:file:".length())).toAbsolutePath().normalize();
                if (file.equals(root) || !file.startsWith(root)) {
                    return false;
                }
            } catch (IllegalArgumentException exception) {
                return false;
            }
        } else {
            return false;
        }

        return supportedH2Options(optionsStart < 0 ? "" : url.substring(optionsStart + 1));
    }

    private static boolean supportedH2Options(String options) {
        if (options.isEmpty()) {
            return true;
        }
        for (String option : options.split(";", -1)) {
            String[] parts = option.split("=", 2);
            if (parts.length != 2) {
                return false;
            }
            String name = parts[0].toUpperCase(java.util.Locale.ROOT);
            String value = parts[1];
            boolean allowed = switch (name) {
                case "MODE" -> "POSTGRESQL".equalsIgnoreCase(value);
                case "DATABASE_TO_LOWER" -> "TRUE".equalsIgnoreCase(value);
                case "DB_CLOSE_DELAY" -> "-1".equals(value);
                case "AUTO_SERVER" -> "FALSE".equalsIgnoreCase(value);
                case "LOCK_TIMEOUT" -> value.matches("[0-9]{1,6}");
                default -> false;
            };
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static Connection open(String url) throws SQLException {
        String username = getRequiredEnvironmentValue("BANKING_DB_USER");
        String password = getRequiredEnvironmentValue("BANKING_DB_PASSWORD");

        return DriverManager.getConnection(url, username, password);
    }

    /** Explicitly scoped local demo/test backend. Never selects a PostgreSQL target. */
    public static void useLocalH2(String url) {
        if (!isLocalH2Url(url)) {
            throw new IllegalArgumentException("Demo/test storage must be local H2 in memory or under tmp");
        }
        localH2Url = url;
    }

    public static void clearLocalH2() {
        localH2Url = null;
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
