package com.vmargin.banking.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoConfigurationTest {
    @Test
    void demoStartupMigratesBeforeInsertOnlySyntheticSeedingAndRerunsSafely() throws Exception {
        String url = "jdbc:h2:mem:demo_runner_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try {
            new ApplicationContextRunner()
                .withUserConfiguration(DemoConfiguration.class)
                .withPropertyValues("spring.profiles.active=demo", "banking.demo.url=" + url)
                .run(context -> {
                    try {
                        ApplicationRunner runner = context.getBean(ApplicationRunner.class);
                        runner.run(new DefaultApplicationArguments());
                        assertDemoCounts();
                        List<Long> seededCounts = demoCounts();

                        runner.run(new DefaultApplicationArguments());
                        assertEquals(seededCounts, demoCounts());
                        assertDemoCounts();
                    } catch (Exception exception) {
                        throw new IllegalStateException("Demo startup runner failed", exception);
                    }
                });
        } finally {
            DatabaseConnection.clearLocalH2();
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                 var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    private void assertDemoCounts() throws Exception {
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM users"));
        assertTrue(scalar("SELECT COUNT(*) FROM money_operations") > 0,
            "Demo startup must seed operation history");
        assertTrue(scalar("SELECT COUNT(*) FROM transactions") > 0,
            "Demo startup must seed ledger activity");
    }

    private List<Long> demoCounts() throws Exception {
        return List.of(
            scalar("SELECT COUNT(*) FROM schema_migrations"),
            scalar("SELECT COUNT(*) FROM users"),
            scalar("SELECT COUNT(*) FROM transactions"),
            scalar("SELECT COUNT(*) FROM money_operations"));
    }

    private long scalar(String sql) throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
