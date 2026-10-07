package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.SavedRecipientService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.util.MoneyValidation;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owns saved recipients and the reviewed internal-transfer flow. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TransferController {
    static final String PENDING_ATTRIBUTE = "pendingTransfer";
    private static final String DASHBOARD = "/transfer";
    private static final String RECIPIENTS = "/transfer#saved-recipients";
    private static final int REVIEW_SECONDS = 300;

    private final TransferService transferService;
    private final SavedRecipientService savedRecipients;
    private final AccountService accountService;
    private final SessionAccounts accounts;

    public TransferController(TransferService transferService, SavedRecipientService savedRecipients,
                              AccountService accountService, SessionAccounts accounts) {
        this.transferService = transferService;
        this.savedRecipients = savedRecipients;
        this.accountService = accountService;
        this.accounts = accounts;
    }

    @GetMapping("/transfer")
    public String transferPage(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        try {
            model.addAttribute("eligibleAccounts", accountService.list(user).stream()
                .filter(account -> "ACTIVE".equals(account.getStatus())).toList());
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("eligibleAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        try {
            model.addAttribute("savedRecipients", savedRecipients.list(user));
            model.addAttribute("savedRecipientsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("savedRecipients", List.of());
            model.addAttribute("savedRecipientsAvailable", false);
        }
        for (String key : List.of("transferError", "transferRecipient", "transferAmount")) {
            SessionFlash.takeValue(session, model, key, key);
        }
        SessionFlash.consumeActions(session, model);
        return "transfer";
    }

    @PostMapping("/recipients")
    public String saveRecipient(@RequestParam(defaultValue = "") String recipientAccountNumber,
                                @RequestParam(defaultValue = "") String recipientMobile,
                                @RequestParam String label, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            String identifier = recipientAccountNumber.isBlank() ? recipientMobile : recipientAccountNumber;
            if (identifier.matches("09\\d{9}")) {
                identifier = accounts.recipient(identifier).getBankAccount().getAccountNumber();
            }
            savedRecipients.save(user, identifier, label);
            session.setAttribute(SessionFlash.SUCCESS_ATTRIBUTE, "Recipient saved for future transfers.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                exception.getSQLState() != null && exception.getSQLState().startsWith("23")
                    ? "That recipient is already saved or the account could not be verified."
                    : "Recipient could not be saved. Check the account and try again.");
        }
        return "redirect:" + RECIPIENTS;
    }

    @PostMapping("/recipients/{recipientId}/delete")
    public String deleteRecipient(@PathVariable long recipientId, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            if (savedRecipients.delete(user, recipientId)) {
                session.setAttribute(SessionFlash.SUCCESS_ATTRIBUTE, "Saved recipient removed.");
            } else {
                session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "Saved recipient was not found on your account.");
            }
        } catch (SQLException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "Saved recipient could not be removed. Try again.");
        }
        return "redirect:" + RECIPIENTS;
    }

    @PostMapping("/transfer/review")
    public String startTransferReview(@RequestParam String recipient, @RequestParam String amount,
                                      @RequestParam(defaultValue = "0") long sourceAccountId,
                                      HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            try {
                BankAccount source = sourceAccountId < 1 ? user.getBankAccount()
                    : accountService.get(user, sourceAccountId).orElseThrow(
                        () -> new IllegalArgumentException("Choose an account on your profile."));
                if (!"ACTIVE".equals(source.getStatus())) {
                    throw new IllegalArgumentException("Choose an active source account.");
                }
                String recipientNumber = recipient.trim();
                if (recipientNumber.matches("09\\d{9}")) {
                    recipientNumber = accounts.recipient(recipientNumber).getBankAccount().getAccountNumber();
                }
                BankAccount target = accountService.recipient(recipientNumber).orElseThrow(
                    () -> new IllegalArgumentException("Recipient account was not found."));
                if (source.getId() == target.getId()) {
                    throw new IllegalArgumentException("Choose a different source and recipient account.");
                }
                if (!source.getCurrencyCode().equals(target.getCurrencyCode())) {
                    throw new IllegalArgumentException(
                        "Transfers between different currencies are unavailable in this demo.");
                }
                BigDecimal value = MoneyValidation.parse(amount);
                if (value.compareTo(source.getBalance()) > 0) {
                    throw new IllegalArgumentException("Transfer amount exceeds your available balance.");
                }
                PendingTransfer pending = new PendingTransfer(UUID.randomUUID().toString(), user.getId(),
                    source.getId(), source.getAccountName(), target.getAccountNumber(), target.getHolderName(),
                    value, Instant.now().plusSeconds(REVIEW_SECONDS));
                session.setAttribute(PENDING_ATTRIBUTE, pending);
                return "redirect:/transfer/review";
            } catch (IllegalArgumentException exception) {
                session.setAttribute("transferError", exception.getMessage());
            } catch (SQLException exception) {
                session.setAttribute("transferError", "Recipient lookup is temporarily unavailable.");
            }
            session.setAttribute("transferRecipient", recipient);
            session.setAttribute("transferAmount", amount);
        }
        return "redirect:" + DASHBOARD;
    }

    @GetMapping("/transfer/review")
    public String transferReview(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingTransfer pending;
        synchronized (session) {
            pending = pending(session);
            if (pending == null || pending.ownerId() != user.getId() || !pending.expires().isAfter(Instant.now())) {
                if (pending != null && pending.ownerId() == user.getId()) {
                    session.removeAttribute(PENDING_ATTRIBUTE);
                }
                session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "The transfer review expired. Start again.");
                return "redirect:" + DASHBOARD;
            }
        }
        model.addAttribute("user", user);
        model.addAttribute("recipient", pending.recipient());
        model.addAttribute("recipientName", pending.recipientName());
        model.addAttribute("sourceAccountName", pending.sourceAccountName());
        model.addAttribute("amount", pending.amount());
        BankAccount source = accountService.get(user, pending.sourceAccountId()).orElse(null);
        if (source == null || !"ACTIVE".equals(source.getStatus())) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                "The source account is no longer available. Start again.");
            return "redirect:" + DASHBOARD;
        }
        model.addAttribute("currencyCode", source.getCurrencyCode());
        BigDecimal transferFee = BigDecimal.ZERO.setScale(2);
        BigDecimal totalDebit = pending.amount().add(transferFee);
        model.addAttribute("transferFee", transferFee);
        model.addAttribute("totalDebit", totalDebit);
        model.addAttribute("operationToken", pending.token());
        model.addAttribute("remainingBalance", source.getBalance().subtract(totalDebit));
        return "transfer-review";
    }

    @PostMapping("/transfer/confirm")
    public String confirmTransfer(@RequestParam(defaultValue = "") String token, HttpSession session)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingTransfer pending;
        synchronized (session) {
            pending = pending(session);
            if (matchesPending(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            } else {
                pending = null;
            }
        }
        if (pending == null) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                "This transfer review expired or was already used. Start again.");
            return "redirect:" + DASHBOARD;
        }
        try {
            transferService.transferToAccount(user, pending.sourceAccountId(), pending.recipient(),
                pending.amount(), pending.token());
            return "redirect:/transactions/receipt/" + pending.token();
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                "Transfer could not be completed. Check your available balance and start again.");
            return "redirect:" + DASHBOARD;
        }
    }

    @PostMapping("/transfer/cancel")
    public String cancelTransferReview(@RequestParam(defaultValue = "") String token, HttpSession session)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            PendingTransfer pending = pending(session);
            if (matchesPending(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            }
        }
        return "redirect:" + DASHBOARD;
    }

    private PendingTransfer pending(HttpSession session) {
        Object value = session.getAttribute(PENDING_ATTRIBUTE);
        return value instanceof PendingTransfer transfer ? transfer : null;
    }

    private boolean matchesPending(PendingTransfer pending, User user, String token) {
        return pending != null && pending.ownerId() == user.getId() && pending.token().equals(token)
            && pending.expires().isAfter(Instant.now());
    }

    record PendingTransfer(String token, long ownerId, long sourceAccountId, String sourceAccountName,
                           String recipient, String recipientName, BigDecimal amount, Instant expires) {
    }
}
