package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.util.MoneyValidation;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owns the local cash-in form and its one-use session token. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CashInController {
    private final CashInService cashInService;
    private final AccountService accountService;
    private final SessionAccounts accounts;
    private final CashInTokens tokens;

    public CashInController(CashInService cashInService, AccountService accountService,
                            SessionAccounts accounts, CashInTokens tokens) {
        this.cashInService = cashInService;
        this.accountService = accountService;
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @GetMapping("/cash-in")
    public String cashInPage(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        try {
            model.addAttribute("eligibleAccounts", accountService.list(user).stream()
                .filter(account -> "ACTIVE".equals(account.getStatus())
                    && "PHP".equals(account.getCurrencyCode())).toList());
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("eligibleAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        model.addAttribute("cashInToken", tokens.current(session, user).token());
        for (String key : List.of("cashInError", "cashInAmount", "cashInDetails")) {
            SessionFlash.takeValue(session, model, key, key);
        }
        SessionFlash.consumeActions(session, model);
        return "cash-in";
    }

    @PostMapping("/cash-in")
    public String cashIn(@RequestParam String amount, @RequestParam String details,
                         @RequestParam(defaultValue = "0") long accountId,
                         @RequestParam(defaultValue = "") String token, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        CashInTokens.Token pending = tokens.consume(session, user.getId(), token);
        if (pending == null || pending.ownerId() != user.getId() || !pending.token().equals(token)
            || !pending.expires().isAfter(java.time.Instant.now())) {
            rememberForm(session, amount, details,
                "This cash-in request expired or was already used. Start again.");
            return "redirect:/cash-in";
        }
        try {
            long destinationAccountId = accountId < 1 ? user.getBankAccount().getId() : accountId;
            cashInService.cashIn(user, destinationAccountId, MoneyValidation.parse(amount), details, token);
            session.setAttribute(SessionFlash.SUCCESS_ATTRIBUTE, "Funds were recorded in your ledger. Reference: "
                + token);
        } catch (SQLException | RuntimeException exception) {
            rememberForm(session, amount, details,
                "Cash-in could not be recorded. Check the amount and details and try again.");
        }
        return "redirect:/cash-in";
    }

    private void rememberForm(HttpSession session, String amount, String details, String error) {
        session.setAttribute("cashInError", error);
        session.setAttribute("cashInAmount", amount);
        session.setAttribute("cashInDetails", details);
    }
}
