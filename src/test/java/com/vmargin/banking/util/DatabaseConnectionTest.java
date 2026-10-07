package com.vmargin.banking.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatabaseConnectionTest {
    @Test
    void localMaintenanceAcceptsOnlyLoopbackV2PostgresWithoutOverrides() {
        assertTrue(DatabaseConnection.isLocalV2PostgresUrl("jdbc:postgresql://localhost:5432/banking_app_v2"));
        assertTrue(DatabaseConnection.isLocalV2PostgresUrl("jdbc:postgresql://127.0.0.1:5433/banking_app_v2"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl("jdbc:postgresql://db.example.com:5432/banking_app_v2"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl("jdbc:postgresql://localhost:5432/banking_app"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl(
            "jdbc:postgresql://localhost:5432/banking_app_v2?sslmode=require"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl(
            "jdbc:postgresql://user:password@localhost:5432/banking_app_v2"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl("jdbc:h2:mem:banking_app_v2"));
        assertFalse(DatabaseConnection.isLocalV2PostgresUrl("malformed"));
    }

    @Test
    void localDemoH2RequiresContainedStorageAndKnownSafeOptions() {
        assertTrue(DatabaseConnection.isLocalH2Url(
            "jdbc:h2:mem:demo_01;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"));
        assertTrue(DatabaseConnection.isLocalH2Url(
            "jdbc:h2:file:./tmp/demo/banking;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=FALSE"));
        assertFalse(DatabaseConnection.isLocalH2Url("jdbc:h2:file:./tmp/../../outside"));
        assertFalse(DatabaseConnection.isLocalH2Url("jdbc:h2:file:./tmp/demo/banking;AUTO_SERVER=TRUE"));
        assertFalse(DatabaseConnection.isLocalH2Url("jdbc:h2:file:./tmp/demo/banking;INIT=RUNSCRIPT FROM 'x'"));
        assertFalse(DatabaseConnection.isLocalH2Url("jdbc:h2:tcp://localhost/mem:remote"));
    }
}
