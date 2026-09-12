package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.CashInRepository;
import com.vmargin.banking.service.exception.InvalidCashInException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CashInServiceTest {

    private User user;
    private RecordingCashInRepository repository;
    private CashInService service;

    @BeforeEach
    void setUp() {
        user = new User(
            1L,
            "09990000001",
            "1234",
            "Test User",
            new BankAccount("ACC-1", "Test User", new BigDecimal("2500.00"))
        );
        repository = new RecordingCashInRepository();
        service = new CashInService(repository);
    }

    @Test
    void cashInUpdatesTheAccountAndDelegatesPersistence() throws Exception {
        BigDecimal balance = service.cashIn(
            user,
            new BigDecimal("125.00"),
            "Cash-in test"
        );

        assertEquals(new BigDecimal("2625.00"), balance);
        assertEquals(new BigDecimal("2625.00"), user.getBalance());
        assertEquals(new BigDecimal("125.00"), repository.amount);
    }

    @Test
    void invalidAmountIsRejectedBeforePersistence() {
        assertThrows(
            InvalidCashInException.class,
            () -> service.cashIn(user, BigDecimal.ZERO, "Cash-in test")
        );

        assertNull(repository.amount);
    }

    private static class RecordingCashInRepository implements CashInRepository {

        private BigDecimal amount;

        @Override
        public BigDecimal cashIn(
            long userId,
            BigDecimal cashInAmount,
            String details,
            LocalDateTime occurredAt
        ) {
            amount = cashInAmount;
            return new BigDecimal("2625.00");
        }
    }
}
