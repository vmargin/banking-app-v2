package com.vmargin.banking.service;

import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.BillPaymentRepository;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class BillPaymentService {
    private final BillPaymentRepository repository;

    public BillPaymentService(BillPaymentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Bill payment repository is required");
    }

    public PreparedPayment prepare(String billerCode, String rawReference, String rawAmount) {
        DemoBiller biller;
        try {
            biller = DemoBiller.valueOf(billerCode == null ? "" : billerCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose one of the listed demo billers.");
        }
        if (rawReference == null || !rawReference.matches("[0-9]{8,20}")) {
            throw new IllegalArgumentException("Bill reference must contain 8 to 20 ASCII digits.");
        }
        BigDecimal amount = MoneyValidation.parse(rawAmount);
        return new PreparedPayment(biller, rawReference.substring(rawReference.length() - 4), amount);
    }

    public void pay(User owner, PreparedPayment payment, String operationReference) throws SQLException {
        Objects.requireNonNull(owner, "Account owner is required");
        pay(owner, owner.getBankAccount().getId(), payment, operationReference);
    }

    public void pay(User owner, long accountId, PreparedPayment payment, String operationReference)
        throws SQLException {
        Objects.requireNonNull(owner, "Account owner is required");
        Objects.requireNonNull(payment, "Prepared bill payment is required");
        repository.payFromAccount(owner.getId(), accountId, payment.biller(), payment.referenceLastFour(),
            payment.amount(), LocalDateTime.now(), operationReference);
    }

    public String newOperationReference() {
        return UUID.randomUUID().toString();
    }

    public record PreparedPayment(DemoBiller biller, String referenceLastFour, BigDecimal amount) {
        public PreparedPayment {
            Objects.requireNonNull(biller, "Demo biller is required");
            if (referenceLastFour == null || !referenceLastFour.matches("[0-9]{4}")) {
                throw new IllegalArgumentException("Bill reference mask is invalid.");
            }
            MoneyValidation.amount(amount);
        }
    }
}
