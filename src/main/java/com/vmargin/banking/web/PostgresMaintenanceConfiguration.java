package com.vmargin.banking.web;

import com.vmargin.banking.repository.JdbcDemoSeedRepository;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.sql.Connection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Explicit local-only PostgreSQL maintenance entry point; inactive for normal application startup. */
@Configuration
@Profile("postgres-maintenance")
public class PostgresMaintenanceConfiguration {
    @Bean
    public ApplicationRunner maintainLocalPostgres(@Value("${banking.maintenance.operation:}") String operation) {
        return args -> {
            if (!"migrate".equals(operation) && !"seed".equals(operation)) {
                throw new IllegalArgumentException("Choose the explicit migrate or seed operation");
            }
            try (Connection connection = DatabaseConnection.openLocalV2Postgres()) {
                if ("migrate".equals(operation)) {
                    new JdbcSchemaMigrator().migrate(connection);
                } else {
                    new JdbcDemoSeedRepository().seedAdministratorIfMissing(connection);
                }
            }
        };
    }
}
