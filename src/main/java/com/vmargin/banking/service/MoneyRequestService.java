package com.vmargin.banking.service;

import com.vmargin.banking.model.MoneyRequest;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.MoneyRequestRepository;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class MoneyRequestService {
    private final MoneyRequestRepository repository;

    public MoneyRequestService(MoneyRequestRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Money request repository is required");
    }

    public List<MoneyRequest> incoming(User payer) throws SQLException {
        return repository.findIncoming(requireUser(payer));
    }

    public List<MoneyRequest> outgoing(User requester) throws SQLException {
        return repository.findOutgoing(requireUser(requester));
    }

    public Optional<MoneyRequest> pendingIncoming(User payer, long requestId) throws SQLException {
        if (requestId < 1) {
            return Optional.empty();
        }
        return repository.findPendingIncoming(requireUser(payer), requestId);
    }

    public MoneyRequest create(User requester, long requesterAccountId, String payerAccountNumber,
                               String rawAmount, String rawNote) throws SQLException {
        long requesterId = requireUser(requester);
        BigDecimal amount = MoneyValidation.parse(rawAmount);
        String accountNumber = AccountService.normalizeAccountNumber(payerAccountNumber);
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.length() > 140) {
            throw new IllegalArgumentException("Add a note of 140 characters or fewer.");
        }
        return repository.create(requesterId, requesterAccountId, accountNumber, amount, note,
            UUID.randomUUID().toString(), LocalDateTime.now());
    }

    public BigDecimal pay(User payer, long requestId, long sourceAccountId, String paymentReference)
        throws SQLException {
        long payerId = requireUser(payer);
        if (requestId < 1 || sourceAccountId < 1) {
            throw new IllegalArgumentException("Choose an active account for this request.");
        }
        return repository.pay(payerId, requestId, sourceAccountId, paymentReference, LocalDateTime.now());
    }

    public boolean decline(User payer, long requestId) throws SQLException {
        if (requestId < 1) {
            return false;
        }
        return repository.decline(requireUser(payer), requestId, LocalDateTime.now());
    }

    public boolean cancel(User requester, long requestId) throws SQLException {
        if (requestId < 1) {
            return false;
        }
        return repository.cancel(requireUser(requester), requestId, LocalDateTime.now());
    }

    private long requireUser(User user) {
        Objects.requireNonNull(user, "Customer is required");
        if (user.getId() < 1) {
            throw new IllegalArgumentException("Customer account is unavailable.");
        }
        return user.getId();
    }
}
