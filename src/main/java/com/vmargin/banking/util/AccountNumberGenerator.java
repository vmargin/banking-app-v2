package com.vmargin.banking.util;

import java.security.SecureRandom;

/** Generates public account numbers for the fictional Nexa demo bank. */
public final class AccountNumberGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long FIRST_TWELVE_DIGIT = 100_000_000_000L;
    private static final long TWELVE_DIGIT_RANGE = 900_000_000_000L;

    private AccountNumberGenerator() {
    }

    public static String next() {
        return Long.toString(FIRST_TWELVE_DIGIT + RANDOM.nextLong(TWELVE_DIGIT_RANGE));
    }
}
