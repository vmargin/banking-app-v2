package com.vmargin.banking.service;

import com.vmargin.banking.model.BankCard;
import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.CardReplacementReason;
import com.vmargin.banking.model.CardReplacementRequest;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.CardRepository;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CardService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CardRepository repository;

    public CardService(CardRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Card repository is required");
    }

    public List<BankCard> list(User owner) throws SQLException {
        return repository.findByOwner(Objects.requireNonNull(owner, "Card owner is required").getId());
    }

    public List<CardReplacementRequest> replacementRequests(User owner) throws SQLException {
        return repository.findReplacementRequests(
            Objects.requireNonNull(owner, "Card owner is required").getId());
    }

    public CardReplacementRequest requestReplacement(User owner, long cardId, String rawReason)
        throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        if (cardId < 1) {
            throw new IllegalArgumentException("Practice card was not found on your profile.");
        }
        CardReplacementReason reason = CardReplacementReason.parse(rawReason);
        return repository.requestReplacement(owner.getId(), cardId, reason, UUID.randomUUID().toString(),
            LocalDateTime.now());
    }

    public boolean cancelReplacementRequest(User owner, long cardId) throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        return cardId > 0 && repository.cancelReplacementRequest(owner.getId(), cardId, LocalDateTime.now());
    }

    public boolean setFrozen(User owner, long cardId, boolean frozen) throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        if (cardId < 1) {
            return false;
        }
        return repository.setFrozen(owner.getId(), cardId, frozen);
    }

    public boolean setOnlineEnabled(User owner, long cardId, boolean enabled) throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        return cardId > 0 && repository.setOnlineEnabled(owner.getId(), cardId, enabled);
    }

    public boolean setDailyLimit(User owner, long cardId, String rawLimit) throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        BigDecimal dailyLimit = MoneyValidation.parse(rawLimit);
        return cardId > 0 && repository.setDailyLimit(owner.getId(), cardId, dailyLimit);
    }

    public BankCard issueDemoCard(User owner, BankAccount account) throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        Objects.requireNonNull(account, "Linked account is required");
        if (account.getOwnerId() != owner.getId() || !"ACTIVE".equals(account.getStatus())
            || !"PHP".equals(account.getCurrencyCode())) {
            throw new IllegalArgumentException("Choose an active PHP account on your profile.");
        }
        long cardsForAccount = list(owner).stream().filter(card -> card.accountId() == account.getId()).count();
        String cardName = cardsForAccount == 0 ? account.getAccountName() + " debit"
            : account.getAccountName() + " debit " + (cardsForAccount + 1);
        String lastFour = "%04d".formatted(RANDOM.nextInt(10_000));
        return repository.createDemoIfMissing(owner.getId(), account.getId(), account.getAccountName(),
            cardName, "VISA", lastFour, new BigDecimal("100000.00"));
    }

    public BankCard createDemoCard(User owner, long accountId, String accountName, String lastFour)
        throws SQLException {
        Objects.requireNonNull(owner, "Card owner is required");
        return repository.createDemoIfMissing(owner.getId(), accountId, accountName,
            "Everyday debit", "VISA", lastFour, new java.math.BigDecimal("100000.00"));
    }
}
