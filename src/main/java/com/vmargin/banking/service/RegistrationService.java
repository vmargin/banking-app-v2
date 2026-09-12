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
            pin,
            fullName.trim(),
            new BankAccount("PENDING", fullName.trim(), BigDecimal.ZERO),
            UserRole.USER
        );
        return repository.save(newUser);
    }

    private void validate(String fullName, String mobileNumber, String pin) {
        if (fullName == null || fullName.isBlank()) {
            throw new RegistrationException("Full name is required");
        }
        if (mobileNumber == null || !mobileNumber.trim().matches("09\\d{9}")) {
            throw new RegistrationException("Use an 11-digit Philippine mobile number");
        }
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new RegistrationException("PIN must contain exactly four digits");
        }
    }
}
