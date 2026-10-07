package com.vmargin.banking.model;

import java.time.LocalDateTime;

public record SavedRecipient(long id, String accountNumber, String label, LocalDateTime createdAt) {
    /** Compatibility accessor for older templates; the value is now a demo account number. */
    public String mobileNumber() {
        return accountNumber;
    }
}
