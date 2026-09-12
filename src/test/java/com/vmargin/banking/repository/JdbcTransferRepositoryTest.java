package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class JdbcTransferRepositoryTest {

    @Test
    void transferDebitsSenderCreditsRecipientAndWritesBothHistoryRows()
        throws SQLException {
        Assumptions.assumeTrue(
            System.getenv("BANKING_DB_USER") != null
                && System.getenv("BANKING_DB_PASSWORD") != null,
            "Database credentials are required"
        );

        UserRepository users = new JdbcUserRepository();
        User sender = users.save(user("09990000001", "Test User", "2500.00"));
        User recipient = users.save(user("09990000002", "Recipient User", "0.00"));

        BigDecimal senderBalance = new JdbcTransferRepository().transfer(
            sender.getId(),
            sender.getMobileNumber(),
            recipient.getMobileNumber(),
            new BigDecimal("500.00"),
            LocalDateTime.now()
        );

        assertEquals(new BigDecimal("2000.00"), senderBalance);
        assertEquals(
            new BigDecimal("2000.00"),
            users.findByMobileNumber(sender.getMobileNumber()).orElseThrow().getBalance()
        );
        assertEquals(
            new BigDecimal("500.00"),
            users.findByMobileNumber(recipient.getMobileNumber()).orElseThrow().getBalance()
        );
        List<Transaction> recipientHistory = new JdbcTransactionRepository().findByUserId(
            recipient.getId()
        );
        assertTrue(recipientHistory.stream().map(Transaction::getType).anyMatch(
            TransactionType.TRANSFER_RECEIVED::equals
        ));
    }

    private User user(String mobileNumber, String fullName, String balance) {
        return new User(
            1L,
            mobileNumber,
            "1234",
            fullName,
            new BankAccount("ACC-" + mobileNumber, fullName, new BigDecimal(balance))
        );
    }
}
