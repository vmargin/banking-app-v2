package com.vmargin.banking.service;

import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.repository.BillPaymentRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BillPaymentServiceTest {
    private final BillPaymentRepository repository = mock(BillPaymentRepository.class);
    private final BillPaymentService service = new BillPaymentService(repository);

    @Test
    void acceptsOnlyAllowlistedBillerAsciiReferenceAndValidMoney() throws Exception {
        var prepared = service.prepare("ELECTRICITY", "001234567890", "42.50");
        assertEquals(DemoBiller.ELECTRICITY, prepared.biller());
        assertEquals("7890", prepared.referenceLastFour());
        assertEquals(new BigDecimal("42.50"), prepared.amount());
        assertThrows(IllegalArgumentException.class, () -> service.prepare("UNKNOWN", "12345678", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare("WATER", "１２３４５６７８", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare("WATER", "1234567", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare("WATER", "12345678", "1.001"));
    }

    @Test
    void paymentPassesOnlyMaskedReferenceToRepository() throws SQLException {
        var payment = service.prepare("WATER", "998877665544", "16.25");
        var owner = ownerWithAccount();
        service.pay(owner, payment, "one-use-operation-token");
        verify(repository).payFromAccount(
            org.mockito.ArgumentMatchers.eq(owner.getId()),
            org.mockito.ArgumentMatchers.eq(owner.getBankAccount().getId()),
            org.mockito.ArgumentMatchers.eq(DemoBiller.WATER),
            org.mockito.ArgumentMatchers.eq("5544"), org.mockito.ArgumentMatchers.eq(new BigDecimal("16.25")),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class),
            org.mockito.ArgumentMatchers.eq("one-use-operation-token")
        );
    }

    private com.vmargin.banking.model.User ownerWithAccount() {
        return new com.vmargin.banking.model.User(42, "09990000042", "hash", "Demo",
            new com.vmargin.banking.model.BankAccount(73, 42, "123456789012", "Demo", "Everyday",
                "CHECKING", "PHP", "ACTIVE", true, BigDecimal.ZERO.setScale(2)));
    }
}
