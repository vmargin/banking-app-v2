package com.vmargin.banking.service;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.SavedRecipient;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.SavedRecipientRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedRecipientServiceTest {
    private User owner;
    private RecordingRepository repository;
    private SavedRecipientService service;

    @BeforeEach
    void setup() {
        owner = new User(7, "09990000001", "unused", "Owner",
            new BankAccount("123456789012", "Owner", java.math.BigDecimal.ZERO));
        repository = new RecordingRepository();
        service = new SavedRecipientService(repository);
    }

    @Test
    void normalizesAccountNumberAndTrimsLabelBeforePersisting() throws Exception {
        service.save(owner, " 0012 3456 7890 ", "  Work  ");
        assertEquals("001234567890", repository.accountNumber);
        assertEquals("Work", repository.label);
        assertEquals(7, repository.ownerId);
    }

    @Test
    void rejectsSelfInvalidMobileAndInvalidLabelBeforeRepositoryWrite() {
        assertThrows(IllegalArgumentException.class,
            () -> service.save(owner, owner.getBankAccount().getAccountNumber(), "Me"));
        assertThrows(IllegalArgumentException.class, () -> service.save(owner, "not-an-account", "Friend"));
        assertThrows(IllegalArgumentException.class, () -> service.save(owner, "001234567890", " "));
        assertThrows(IllegalArgumentException.class,
            () -> service.save(owner, "001234567890", "x".repeat(41)));
        assertEquals(null, repository.accountNumber);
    }

    private static final class RecordingRepository implements SavedRecipientRepository {
        private long ownerId;
        private String accountNumber;
        private String label;

        @Override public List<SavedRecipient> findByOwner(long id) { return List.of(); }
        @Override public void save(long id, String ownerMobile, String recipientAccountNumber, String savedLabel) {
            ownerId = id;
            accountNumber = recipientAccountNumber;
            label = savedLabel;
        }
        @Override public boolean delete(long id, long recipientId) { return false; }
    }
}
