package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.CardReplacementRequest;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.CardService;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owner-scoped account and practice-card controls. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountPortfolioController {
    private static final String NOTICE = "portfolioNotice";
    private static final String ERROR = "portfolioError";
    private final SessionAccounts sessions;
    private final AccountService accounts;
    private final CardService cards;

    public AccountPortfolioController(SessionAccounts sessions, AccountService accounts, CardService cards) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.cards = cards;
    }

    @GetMapping("/accounts")
    public String accounts(HttpSession session, Model model) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", owner);
        try {
            List<BankAccount> bankAccounts = accounts.list(owner);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("phpTotal", bankAccounts.stream()
                .filter(account -> "PHP".equals(account.getCurrencyCode()))
                .map(BankAccount::getBalance).reduce(java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal::add));
            model.addAttribute("usdTotal", bankAccounts.stream()
                .filter(account -> "USD".equals(account.getCurrencyCode()))
                .map(BankAccount::getBalance).reduce(java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal::add));
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("bankAccounts", List.of());
            model.addAttribute("phpTotal", java.math.BigDecimal.ZERO.setScale(2));
            model.addAttribute("usdTotal", java.math.BigDecimal.ZERO.setScale(2));
            model.addAttribute("accountsAvailable", false);
        }
        flash(session, model);
        return "accounts";
    }

    @PostMapping("/accounts")
    public String openAccount(@RequestParam String name, @RequestParam String currencyCode,
                              HttpSession session) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            var account = accounts.open(owner, name, currencyCode);
            session.setAttribute(NOTICE, account.getCurrencyCode() + " demo account opened with a zero balance.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "That account could not be opened. Try a different name.");
        }
        return "redirect:/accounts";
    }

    @GetMapping("/cards")
    public String cards(HttpSession session, Model model) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", owner);
        try {
            List<BankAccount> bankAccounts = accounts.list(owner);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("eligibleCardAccounts", bankAccounts.stream()
                .filter(account -> "ACTIVE".equals(account.getStatus())
                    && "PHP".equals(account.getCurrencyCode())).toList());
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("bankAccounts", List.of());
            model.addAttribute("eligibleCardAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        try {
            model.addAttribute("cards", cards.list(owner));
            model.addAttribute("cardsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("cards", List.of());
            model.addAttribute("cardsAvailable", false);
        }
        try {
            List<CardReplacementRequest> replacements = cards.replacementRequests(owner);
            model.addAttribute("replacementRequests", replacements);
            model.addAttribute("openReplacementByCard", replacements.stream()
                .filter(CardReplacementRequest::isOpen)
                .collect(Collectors.toMap(CardReplacementRequest::cardId, Function.identity(),
                    (first, ignored) -> first)));
            model.addAttribute("replacementRequestsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("replacementRequests", List.of());
            model.addAttribute("openReplacementByCard", Map.of());
            model.addAttribute("replacementRequestsAvailable", false);
        }
        flash(session, model);
        return "cards";
    }

    @PostMapping("/cards")
    public String issueCard(@RequestParam long accountId, HttpSession session) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            BankAccount account = accounts.get(owner, accountId).orElseThrow(
                () -> new IllegalArgumentException("Choose an account on your profile."));
            cards.issueDemoCard(owner, account);
            session.setAttribute(NOTICE, "Practice debit card added. It is not connected to a payment network.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Practice card could not be added. Check the linked account and try again.");
        }
        return "redirect:/cards";
    }

    @PostMapping("/cards/{cardId}/freeze")
    public String freeze(@PathVariable long cardId, @RequestParam boolean frozen, HttpSession session)
        throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            if (!cards.setFrozen(owner, cardId, frozen)) {
                throw new IllegalArgumentException("Practice card was not found or has an open replacement request.");
            }
            session.setAttribute(NOTICE, frozen ? "Practice card frozen in this local ledger."
                : "Practice card is active in this local ledger.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Practice card status could not be changed. Try again.");
        }
        return "redirect:/cards";
    }

    @PostMapping("/cards/{cardId}/replacement")
    public String requestReplacement(@PathVariable long cardId, @RequestParam String reason, HttpSession session)
        throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            cards.requestReplacement(owner, cardId, reason);
            session.setAttribute(NOTICE, "Local replacement request saved and practice card frozen. "
                + "No issuer was contacted.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Replacement request could not be saved. Try again.");
        }
        return "redirect:/cards";
    }

    @PostMapping("/cards/{cardId}/replacement/cancel")
    public String cancelReplacement(@PathVariable long cardId, HttpSession session) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        boolean canceled = cards.cancelReplacementRequest(owner, cardId);
        session.setAttribute(canceled ? NOTICE : ERROR, canceled
            ? "Local replacement request canceled. The practice card remains frozen until you reactivate it."
            : "No open replacement request was found for that card.");
        return "redirect:/cards";
    }

    @PostMapping("/cards/{cardId}/online")
    public String onlinePayments(@PathVariable long cardId, @RequestParam boolean enabled, HttpSession session)
        throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            if (!cards.setOnlineEnabled(owner, cardId, enabled)) {
                throw new IllegalArgumentException("Practice card was not found on your profile.");
            }
            session.setAttribute(NOTICE, "Online purchase preference saved for this local card record.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Online purchase preference could not be saved. Try again.");
        }
        return "redirect:/cards";
    }

    @PostMapping("/cards/{cardId}/limit")
    public String dailyLimit(@PathVariable long cardId, @RequestParam String amount, HttpSession session)
        throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            if (!cards.setDailyLimit(owner, cardId, amount)) {
                throw new IllegalArgumentException("Practice card was not found on your profile.");
            }
            session.setAttribute(NOTICE, "Daily practice limit saved. No payment network is connected.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Daily practice limit could not be saved. Try again.");
        }
        return "redirect:/cards";
    }

    private void flash(HttpSession session, Model model) {
        Object notice = session.getAttribute(NOTICE);
        Object error = session.getAttribute(ERROR);
        if (notice instanceof String message) {
            model.addAttribute("portfolioNotice", message);
            session.removeAttribute(NOTICE);
        }
        if (error instanceof String message) {
            model.addAttribute("portfolioError", message);
            session.removeAttribute(ERROR);
        }
    }
}
