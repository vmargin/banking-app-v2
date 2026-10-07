package com.vmargin.banking.web;

import com.vmargin.banking.repository.JdbcDemoSeedRepository;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.sql.Connection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.ApplicationRunner;

@Configuration
@Profile("demo")
public class DemoConfiguration {
    @Bean
    public ApplicationRunner initializeDemo(@Value("${banking.demo.url}") String url) {
        return args -> {
            DatabaseConnection.useLocalH2(url);
            try (Connection connection = DatabaseConnection.open()) {
                new JdbcSchemaMigrator().migrate(connection);
            }
            new JdbcDemoSeedRepository().seedNexaDemo();
        };
    }
}
