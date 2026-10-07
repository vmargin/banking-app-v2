package com.vmargin.banking.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class PostgresMaintenanceConfigurationTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
        .withUserConfiguration(PostgresMaintenanceConfiguration.class);

    @Test
    void normalApplicationProfileDoesNotRegisterMaintenanceRunner() {
        contexts.run(context -> assertTrue(context.getBeansOfType(ApplicationRunner.class).isEmpty()));
    }

    @Test
    void maintenanceProfileRequiresAnExplicitOperationBeforeConnecting() {
        contexts.withPropertyValues("spring.profiles.active=postgres-maintenance")
            .run(context -> {
                ApplicationRunner runner = context.getBean(ApplicationRunner.class);
                ApplicationArguments arguments = new DefaultApplicationArguments();
                assertThrows(IllegalArgumentException.class, () -> runner.run(arguments));
            });
    }

    @Test
    void completeMaintenanceApplicationLoadsWithoutServletOrDatabaseConnections() {
        new ApplicationContextRunner()
            .withUserConfiguration(BankingWebApplication.class)
            .withPropertyValues(
                "spring.main.web-application-type=none",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "spring.sql.init.mode=never",
                "spring.profiles.active=postgres-maintenance",
                "banking.maintenance.operation=migrate")
            .run(context -> {
                assertTrue(context.containsBean("maintainLocalPostgres"));
                assertTrue(context.getBeansOfType(BankingWebController.class).isEmpty());
                assertTrue(context.getBeansOfType(SecurityFilterChain.class).isEmpty());
            });
    }
}
