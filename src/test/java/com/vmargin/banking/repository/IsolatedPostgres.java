package com.vmargin.banking.repository;

final class IsolatedPostgres {
    private IsolatedPostgres() {
    }

    static boolean enabled() {
        String url = System.getenv("BANKING_DB_URL");
        return "true".equals(System.getenv("BANKING_DB_ISOLATED_TESTS")) && isSafeTestDatabaseUrl(url);
    }

    static boolean isSafeTestDatabaseUrl(String url) {
        return url != null && url.matches(
            "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]{1,5}/banking_app_v2_test"
        );
    }
}
