package com.vmargin.banking.service;

import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransferRepository;
import com.vmargin.banking.service.exception.InvalidTransferException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

public class TransferService {

    private final TransferRepository repository;

    public TransferService(TransferRepository repository) {
        this.repository = Objects.requireNonNull(
            repository,
            "Transfer repository is required"
        );
    }

    public BigDecimal transfer(User sender, String recipientMobileNumber, BigDecimal amount)
        throws SQLException {
        return transfer(sender, recipientMobileNumber, amount, java.util.UUID.randomUUID().toString());
    }

    public BigDecimal transfer(User sender, String recipientMobileNumber, BigDecimal amount, String reference)
        throws SQLException {
        Objects.requireNonNull(sender, "Sender is required");
        validateRecipient(sender, recipientMobileNumber);
        validateAmount(amount);

        BigDecimal updatedBalance = repository.transfer(
            sender.getId(),
            sender.getMobileNumber(),
            recipientMobileNumber.trim(),
            amount,
            LocalDateTime.now(),
            reference
        );
        return updatedBalance;
    }

    public BigDecimal transferToAccount(User sender, long sourceAccountId, String recipientAccountNumber,
                                        BigDecimal amount, String reference) throws SQLException {
        Objects.requireNonNull(sender, "Sender is required");
        if (sourceAccountId < 1) {
            throw new InvalidTransferException("Choose an available source account");
        }
        AccountService.normalizeAccountNumber(recipientAccountNumber);
        validateAmount(amount);
        return repository.transferToAccount(sender.getId(), sourceAccountId, recipientAccountNumber.trim(),
            amount, LocalDateTime.now(), reference);
    }

    private void validateRecipient(User sender, String recipientMobileNumber) {
        if (recipientMobileNumber == null || !recipientMobileNumber.trim().matches("09\\d{9}")) {
            throw new InvalidTransferException("Recipient mobile number is required");
        }
        if (sender.getMobileNumber().equals(recipientMobileNumber.trim())) {
            throw new InvalidTransferException("You cannot transfer to your own account");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new InvalidTransferException(
                "Transfer amount cannot have more than 2 decimals"
            );
        }
        if (amount.compareTo(com.vmargin.banking.util.MoneyValidation.MAXIMUM) > 0) {
            throw new InvalidTransferException("Transfer amount exceeds the supported limit");
        }
    }
}
