package com.vmargin.banking.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PinHasher {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private PinHasher() {
    }

    public static String hash(String pin) {
        return ENCODER.encode(pin);
    }

    public static boolean isHash(String value) {
        return value != null && value.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    }

    public static boolean matches(String candidate, String stored) {
        if (candidate == null || stored == null) {
            return false;
        }
        if (isHash(stored)) {
            return ENCODER.matches(candidate, stored);
        }
        return stored.matches("\\d{4}") && MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }
}
