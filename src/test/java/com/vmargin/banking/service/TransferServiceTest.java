package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransferRepository;
import com.vmargin.banking.service.exception.InvalidTransferException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private User sender;
    private RecordingTransferRepository repository;
    private TransferService service;

    @BeforeEach
    void setUp() {
        sender = new User(
            1L,
            "09990000001",
            "1234",
            "Test User",
            new BankAccount("ACC-1", "Test User", new BigDecimal("2500.00"))
        );
        repository = new RecordingTransferRepository();
        service = new TransferService(repository);
    }

    @Test
    void transferDebitsTheSenderAfterPersistenceSucceeds() throws Exception {
        BigDecimal balance = service.transfer(
            sender,
            "09990000002",
            new BigDecimal("500.00")
        );

        assertEquals(new BigDecimal("2000.00"), balance);
        assertEquals(new BigDecimal("2000.00"), sender.getBalance());
        assertEquals("09990000002", repository.recipientMobileNumber);
        assertEquals(new BigDecimal("500.00"), repository.amount);
    }

    @Test
    void transferRejectsTheAuthenticatedUsersOwnMobileNumber() {
        assertThrows(
            InvalidTransferException.class,
            () -> service.transfer(sender, "09990000001", new BigDecimal("500.00"))
        );

        assertNull(repository.amount);
    }

    private static class RecordingTransferRepository implements TransferRepository {

        private String recipientMobileNumber;
        private BigDecimal amount;

        @Override
        public BigDecimal transfer(
            long senderId,
            String senderMobileNumber,
            String recipientMobile,
            BigDecimal transferAmount,
            LocalDateTime occurredAt
        ) {
            recipientMobileNumber = recipientMobile;
            amount = transferAmount;
            return new BigDecimal("2000.00");
        }
    }
}
