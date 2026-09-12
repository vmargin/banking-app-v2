package com.vmargin.banking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class BankAccountTest {

    @Test
    void constructorStoresValidAccountState() {
        BankAccount account = new BankAccount(
            "ACC-001",
            "Valkenburgh Margin",
            new BigDecimal("1000.00")
        );

        assertEquals("ACC-001", account.getAccountId());
        assertEquals("Valkenburgh Margin", account.getHolderName());
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    void constructorRejectsNegativeOpeningBalance() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new BankAccount(
                "ACC-001",
                "Valkenburgh Margin",
                new BigDecimal("-1.00")
            )
        );
    }

    @Test
    void depositAddsPositiveAmount() {
        BankAccount account = new BankAccount(
            "ACC-001",
            "Valkenburgh Margin",
            new BigDecimal("1000.00")
        );

        account.deposit(new BigDecimal("500.00"));

        assertEquals(new BigDecimal("1500.00"), account.getBalance());
    }

    @Test
    void depositRejectsZeroAmountAndPreservesBalance() {
        BankAccount account = new BankAccount(
            "ACC-001",
            "Valkenburgh Margin",
            new BigDecimal("1000.00")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> account.deposit(new BigDecimal("0.00"))
        );

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    void withdrawSubtractsAvailableAmount() {
        BankAccount account = new BankAccount(
            "ACC-001",
            "Valkenburgh Margin",
            new BigDecimal("1000.00")
        );

        account.withdraw(new BigDecimal("400.00"));

        assertEquals(new BigDecimal("600.00"), account.getBalance());
    }

    @Test
    void withdrawRejectsInsufficientFundsAndPreservesBalance() {
        BankAccount account = new BankAccount(
            "ACC-001",
            "Valkenburgh Margin",
            new BigDecimal("1000.00")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> account.withdraw(new BigDecimal("1000.01"))
        );

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }
}
