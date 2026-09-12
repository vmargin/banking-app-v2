package com.vmargin.banking.service;

import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.AccountLockedException;
import com.vmargin.banking.service.exception.InvalidCredentialsException;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public class LoginService {

    private static final int MAX_ATTEMPTS = 3;

    private final UserRepository repository;
    private int failedAttempts;
    private boolean locked;

    public LoginService(UserRepository repository) {
        this.repository = Objects.requireNonNull(
            repository,
            "User repository is required"
        );
    }

    public User login(String mobile, String pin) throws SQLException {
        if (locked) {
            throw new AccountLockedException(
                "Login is locked after three failed attempts"
            );
        }

        if (mobile == null || mobile.isBlank()
            || pin == null || pin.isBlank()) {
            throw new InvalidCredentialsException(
                "Mobile number and PIN are required"
            );
        }

        Optional<User> user = repository.findByMobileNumber(mobile);

        if (user.isEmpty()) {
            recordFailedAttempt();
            throw new InvalidCredentialsException("User not found");
        }

        User authenticatedUser = user.get();

        if (!authenticatedUser.matchesPin(pin)) {
            recordFailedAttempt();
            throw new InvalidCredentialsException("Incorrect PIN");
        }

        failedAttempts = 0;
        return authenticatedUser;
    }

    private void recordFailedAttempt() {
        failedAttempts++;

        if (failedAttempts >= MAX_ATTEMPTS) {
            locked = true;
            throw new AccountLockedException(
                "Login is locked after three failed attempts"
            );
        }
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public int getRemainingAttempts() {
        return MAX_ATTEMPTS - failedAttempts;
    }

    public boolean isLocked() {
        return locked;
    }
}
