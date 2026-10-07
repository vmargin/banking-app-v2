package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.SavingsGoal;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.CardService;
import com.vmargin.banking.service.SavedRecipientService;
import com.vmargin.banking.service.SavingsGoalService;
import com.vmargin.banking.service.TransactionHistoryService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Composes the authenticated dashboard from owner-scoped account services. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DashboardController {
    private final SessionAccounts accounts;
    private final TransactionHistoryService historyService;
    private final AccountService accountService;
    private final CardService cardService;
    private final SavedRecipientService savedRecipients;
    private final SavingsGoalService savingsGoals;
    private final CashInTokens cashInTokens;
    private final TransactionActivityModelAssembler activityModel;

    public DashboardController(SessionAccounts accounts, TransactionHistoryService historyService,
                               AccountService accountService, CardService cardService,
                               SavedRecipientService savedRecipients, SavingsGoalService savingsGoals,
                               CashInTokens cashInTokens, TransactionActivityModelAssembler activityModel) {
        this.accounts = accounts;
        this.historyService = historyService;
        this.accountService = accountService;
        this.cardService = cardService;
        this.savedRecipients = savedRecipients;
        this.savingsGoals = savingsGoals;
        this.cashInTokens = cashInTokens;
        this.activityModel = activityModel;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model,
                            @RequestParam(required = false) String type,
                            @RequestParam(required = false) String fromDate,
                            @RequestParam(required = false) String toDate,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String accountId,
                            @RequestParam(required = false) String page,
                            @RequestParam(required = false) String pageSize,
                            @RequestParam(required = false) String action) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("activeAction", switch (action == null ? "" : action) {
            case "cash-in", "transfer", "recipients", "bill-payment" -> action;
            default -> "";
        });
        model.addAttribute("demoBillers", DemoBiller.values());
        addMonthlyInsights(user, model);
        addAccounts(user, model);
        addCards(user, model);
        addSavedRecipients(user, model);
        addSavingsGoals(user, model);
        SessionFlash.consumeActions(session, model);
        for (String key : List.of("cashInError", "cashInAmount", "cashInDetails",
                                  "transferError", "transferRecipient", "transferAmount")) {
            SessionFlash.takeValue(session, model, key, key);
        }
        model.addAttribute("cashInToken", cashInTokens.current(session, user).token());
        activityModel.populate(user, model, type, fromDate, toDate, search, accountId, page, pageSize);
        return "dashboard";
    }

    private void addMonthlyInsights(User user, Model model) {
        YearMonth month = YearMonth.now();
        try {
            model.addAttribute("monthlyInsights", historyService.getMonthlyInsights(user, month));
            model.addAttribute("monthlyInsightsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("monthlyInsights", MonthlyTransactionInsights.empty(month));
            model.addAttribute("monthlyInsightsAvailable", false);
        }
    }

    private void addAccounts(User user, Model model) {
        try {
            List<BankAccount> bankAccounts = accountService.list(user);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("accountsAvailable", true);
            model.addAttribute("phpTotal", bankAccounts.stream()
                .filter(account -> "PHP".equals(account.getCurrencyCode()))
                .map(BankAccount::getBalance).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        } catch (SQLException exception) {
            model.addAttribute("bankAccounts", List.of(user.getBankAccount()));
            model.addAttribute("accountsAvailable", false);
            model.addAttribute("phpTotal", user.getBalance());
        }
    }

    private void addCards(User user, Model model) {
        try {
            model.addAttribute("cards", cardService.list(user));
            model.addAttribute("cardsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("cards", List.of());
            model.addAttribute("cardsAvailable", false);
        }
    }

    private void addSavedRecipients(User user, Model model) {
        try {
            model.addAttribute("savedRecipients", savedRecipients.list(user));
            model.addAttribute("savedRecipientsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("savedRecipients", List.of());
            model.addAttribute("savedRecipientsAvailable", false);
        }
    }

    private void addSavingsGoals(User user, Model model) {
        try {
            List<SavingsGoal> previewGoals = savingsGoals.list(user).stream()
                .sorted(Comparator.comparingInt(SavingsGoal::progressPercent).reversed())
                .limit(3)
                .toList();
            model.addAttribute("savingsGoals", previewGoals);
            model.addAttribute("savingsGoalsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("savingsGoals", List.of());
            model.addAttribute("savingsGoalsAvailable", false);
        }
    }
}
