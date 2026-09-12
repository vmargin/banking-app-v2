package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.model.User;
import java.sql.SQLException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class JdbcAdminRoleRepositoryTest {

    @Test
    void readsTheSeededAdministratorRoleFromPostgreSql() throws SQLException {
        Assumptions.assumeTrue(
            System.getenv("BANKING_DB_USER") != null
                && System.getenv("BANKING_DB_PASSWORD") != null,
            "Database credentials are required"
        );

        JdbcUserRepository repository = new JdbcUserRepository();
        User administrator = repository.findByMobileNumber("09990000000").orElseThrow();

        assertTrue(administrator.isAdmin());
        assertTrue(repository.findAll().stream().map(User::getId).anyMatch(
            userId -> userId == administrator.getId()
        ));
    }
}
