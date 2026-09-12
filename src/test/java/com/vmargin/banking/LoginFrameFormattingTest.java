package com.vmargin.banking;

import com.vmargin.banking.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginFrameFormattingTest {

    @Test
    void formatsPhilippinePesoAmountsWithGroupingAndTwoDecimals() {
        assertEquals(
            "PHP 125,000.50",
            LoginFrame.formatCurrency(new BigDecimal("125000.5"))
        );
    }

    @Test
    void marksCashInAndReceivedTransfersAsIncoming() {
        assertEquals(
            "+ PHP 5,000.00",
            LoginFrame.formatTransactionAmount(
                TransactionType.CASH_IN,
                new BigDecimal("5000")
            )
        );
        assertEquals(
            "+ PHP 750.00",
            LoginFrame.formatTransactionAmount(
                TransactionType.TRANSFER_RECEIVED,
                new BigDecimal("750")
            )
        );
    }

    @Test
    void marksSentTransfersAsOutgoing() {
        assertEquals(
            "- PHP 1,250.50",
            LoginFrame.formatTransactionAmount(
                TransactionType.TRANSFER_SENT,
                new BigDecimal("1250.50")
            )
        );
    }
}
