package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IsolatedPostgresTest {
    @Test
    void testDatabaseGuardRejectsUrlsWithEffectiveTargetOverrides() {
        assertTrue(IsolatedPostgres.isSafeTestDatabaseUrl(
            "jdbc:postgresql://localhost:5432/banking_app_v2_test"));
        assertTrue(IsolatedPostgres.isSafeTestDatabaseUrl(
            "jdbc:postgresql://127.0.0.1:55432/banking_app_v2_test"));
        assertFalse(IsolatedPostgres.isSafeTestDatabaseUrl(
            "jdbc:postgresql://localhost:5432/banking_app_v2_test?PGHOST=remote.example&PGDBNAME=banking_app"));
        assertFalse(IsolatedPostgres.isSafeTestDatabaseUrl(
            "jdbc:postgresql://db.example.com:5432/banking_app_v2_test"));
        assertFalse(IsolatedPostgres.isSafeTestDatabaseUrl(
            "jdbc:postgresql://localhost:5432/banking_app"));
    }
}
