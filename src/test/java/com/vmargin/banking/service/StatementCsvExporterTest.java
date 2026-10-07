package com.vmargin.banking.service;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementCsvExporterTest {
    @Test
    void quotesCellsNeutralizesFormulaTextAndKeepsSignedMoneyAndSharedReference() {
        String reference = "8d11eaa1-a3dd-4e1d-a980-7ed32b3b1d8f";
        List<Transaction> activity = List.of(
            transaction(1, 1, TransactionType.CASH_IN, "100.00", "\t=HYPERLINK(\"example\")", "cash-ref"),
            transaction(2, 1, TransactionType.TRANSFER_SENT, "30.00", "Rent, \"Unit\"\r\nFloor 2", reference),
            transaction(3, 2, TransactionType.TRANSFER_RECEIVED, "30.00", "+Injected", reference),
            transaction(4, 1, TransactionType.BILL_PAYMENT, "12.50",
                "Simulated bill payment · Demo water · reference ending 9012", "bill-ref")
        );

        String csv = new StatementCsvExporter().export(activity);

        assertTrue(csv.startsWith("Date,Account,Currency,Type,Category,Direction,Amount,Details,Reference\r\n"));
        assertTrue(csv.contains("\"+100.00\""));
        assertTrue(csv.contains("\"-30.00\""));
        assertTrue(csv.contains("\"'\t=HYPERLINK("));
        assertTrue(csv.contains("\"Rent, \"\"Unit\"\"\r\nFloor 2\""));
        assertTrue(csv.contains("\"'+Injected\""));
        assertTrue(csv.contains("\"Everyday account\",\"PHP\",\"BILL_PAYMENT\","
            + "\"Bills & Utilities\",\"OUT\",\"-12.50\""));
        assertTrue(csv.contains("reference ending 9012"));
        assertTrue(!csv.contains("123456789012"));
        assertTrue(csv.indexOf(reference) != csv.lastIndexOf(reference));
        assertTrue(csv.endsWith("\r\n"));
    }

    private Transaction transaction(long id, long userId, TransactionType type, String amount,
                                    String details, String reference) {
        return new Transaction(id, userId, 1, "Everyday account", "PHP", type, new BigDecimal(amount), details,
            LocalDateTime.of(2026, 9, 1, 12, 30), reference);
    }
}
