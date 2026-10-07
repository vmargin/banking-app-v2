package com.vmargin.banking.service;

import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.AccountLockedException;
import com.vmargin.banking.service.exception.InvalidCredentialsException;
import com.vmargin.banking.util.PinHasher;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class LoginService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_KEYS = 10000;
    private static final Duration LOCK_TIME = Duration.ofMinutes(5);
    private static final String ERROR = "Unable to sign in. Check your credentials or try again later.";
    private static final String DUMMY_HASH = PinHasher.hash("0000");
    private final UserRepository repository;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new LinkedHashMap<>();
    private final ThreadLocal<String> lastMobile = new ThreadLocal<>();

    public LoginService(UserRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public LoginService(UserRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized User login(String mobile, String pin) throws SQLException {
        if (mobile == null || !mobile.trim().matches("09\\d{9}") || pin == null || !pin.matches("\\d{4}")) {
            throw new InvalidCredentialsException(ERROR);
        }
        String key = mobile.trim();
        lastMobile.set(key);
        Instant now = clock.instant();
        clearExpiredAttempts(now);
        checkNotLocked(key);
        User user = repository.findByMobileNumber(key).orElse(null);
        boolean matches = PinHasher.matches(pin, user == null ? DUMMY_HASH : user.getPinForPersistence());
        if (user == null || !matches) {
            recordFailure(key, now);
            throw new InvalidCredentialsException(ERROR);
        }
        String storedPin = user.getPinForPersistence();
        if (!PinHasher.isHash(storedPin)) {
            String upgradedHash = PinHasher.hash(pin);
            repository.updatePin(user.getId(), storedPin, upgradedHash);
            user = withPin(user, upgradedHash);
        }
        attempts.remove(key);
        return user;
    }

    public synchronized void changePin(long userId, String currentPin, String newPin, String confirmation)
        throws SQLException {
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new IllegalArgumentException("New PIN must contain exactly four digits.");
        }
        if (!newPin.equals(confirmation)) {
            throw new IllegalArgumentException("The new PIN entries do not match.");
        }

        User user = repository.findById(userId).orElse(null);
        if (user == null) {
            throw new InvalidCredentialsException(ERROR);
        }
        String key = user.getMobileNumber();
        lastMobile.set(key);
        Instant now = clock.instant();
        clearExpiredAttempts(now);
        checkNotLocked(key);

        String storedPin = user.getPinForPersistence();
        if (currentPin == null || !currentPin.matches("\\d{4}") || !PinHasher.matches(currentPin, storedPin)) {
            recordFailure(key, now);
            throw new InvalidCredentialsException(ERROR);
        }
        if (newPin.equals(currentPin)) {
            throw new IllegalArgumentException("Choose a new PIN that differs from your current PIN.");
        }

        repository.updatePin(userId, storedPin, PinHasher.hash(newPin));
        attempts.remove(key);
    }

    public synchronized int getFailedAttempts() {
        Attempts state = attempts.get(lastMobile.get());
        return state == null || !state.expires().isAfter(clock.instant()) ? 0 : state.count();
    }

    public int getRemainingAttempts() {
        return MAX_ATTEMPTS - getFailedAttempts();
    }

    public boolean isLocked() {
        return getFailedAttempts() >= MAX_ATTEMPTS;
    }

    private void clearExpiredAttempts(Instant now) {
        attempts.entrySet().removeIf(entry -> !entry.getValue().expires().isAfter(now));
    }

    private void checkNotLocked(String key) {
        Attempts state = attempts.get(key);
        if (state != null && state.count() >= MAX_ATTEMPTS) {
            throw new AccountLockedException(ERROR);
        }
    }

    private void recordFailure(String key, Instant now) {
        Attempts state = attempts.get(key);
        if (state == null && attempts.size() >= MAX_KEYS) {
            // Do not evict locked identities to make space for attacker-controlled keys.
            throw new AccountLockedException(ERROR);
        }
        int count = state == null ? 1 : state.count() + 1;
        attempts.put(key, new Attempts(count, now.plus(LOCK_TIME)));
        if (count >= MAX_ATTEMPTS) {
            throw new AccountLockedException(ERROR);
        }
    }

    private User withPin(User user, String pin) {
        return new User(user.getId(), user.getMobileNumber(), pin, user.getFullName(), user.getBankAccount(),
            user.getRole());
    }

    private record Attempts(int count, Instant expires) {
    }
}
