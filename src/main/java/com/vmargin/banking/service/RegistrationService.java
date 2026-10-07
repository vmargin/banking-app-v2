package com.vmargin.banking.service;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.RegistrationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;

public class RegistrationService {

    private final UserRepository repository;

    public RegistrationService(UserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "User repository is required");
    }

    public User register(String fullName, String mobileNumber, String pin) throws SQLException {
        validate(fullName, mobileNumber, pin);
        if (repository.findByMobileNumber(mobileNumber.trim()).isPresent()) {
            throw new RegistrationException("A user already exists for this mobile number");
        }

        User newUser = new User(
            0L,
            mobileNumber.trim(),
            com.vmargin.banking.util.PinHasher.hash(pin),
            fullName.trim(),
            new BankAccount("PENDING", fullName.trim(), BigDecimal.ZERO),
            UserRole.USER
        );
        try {
            return repository.save(newUser);
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new RegistrationException("A user already exists for this mobile number");
            }
            throw exception;
        }
    }

    private void validate(String fullName, String mobileNumber, String pin) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 120) {
            throw new RegistrationException("Full name must contain 1 to 120 characters");
        }
        if (mobileNumber == null || !mobileNumber.trim().matches("09\\d{9}")) {
            throw new RegistrationException("Use an 11-digit Philippine mobile number");
        }
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new RegistrationException("PIN must contain exactly four digits");
        }
    }
}
