package com.vmargin.banking.service;

import com.vmargin.banking.model.SavedRecipient;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.SavedRecipientRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class SavedRecipientService {
    private final SavedRecipientRepository repository;

    public SavedRecipientService(SavedRecipientRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Saved recipient repository is required");
    }

    public List<SavedRecipient> list(User owner) throws SQLException {
        return repository.findByOwner(Objects.requireNonNull(owner).getId());
    }

    public void save(User owner, String identifier, String suppliedLabel) throws SQLException {
        Objects.requireNonNull(owner, "Owner is required");
        String accountNumber = AccountService.normalizeAccountNumber(identifier);
        String label = suppliedLabel == null ? "" : suppliedLabel.trim();
        if (label.isEmpty() || label.length() > 40) {
            throw new IllegalArgumentException("Label must be between 1 and 40 characters.");
        }
        if (accountNumber.equals(owner.getBankAccount().getAccountNumber())) {
            throw new IllegalArgumentException("You cannot save your own account.");
        }
        repository.save(owner.getId(), owner.getMobileNumber(), accountNumber, label);
    }

    public boolean delete(User owner, long recipientId) throws SQLException {
        if (owner == null || recipientId < 1) {
            return false;
        }
        return repository.delete(owner.getId(), recipientId);
    }

}
