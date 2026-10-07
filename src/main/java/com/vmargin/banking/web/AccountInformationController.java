package com.vmargin.banking.web;

import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.TransactionHistoryService;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Read-only account insights, ledger notices, and simulator help. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountInformationController {
    private static final int RECENT_NOTICE_LIMIT = 10;
    private final SessionAccounts accounts;
    private final TransactionHistoryService historyService;

    public AccountInformationController(SessionAccounts accounts, TransactionHistoryService historyService) {
        this.accounts = accounts;
        this.historyService = historyService;
    }

    @GetMapping("/insights")
    public String insights(@RequestParam(required = false) String month, HttpSession session, Model model)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }

        YearMonth currentMonth = YearMonth.now();
        YearMonth selectedMonth = currentMonth;
        String monthError = null;
        if (month != null && !month.isBlank()) {
            try {
                YearMonth requestedMonth = YearMonth.parse(month);
                if (requestedMonth.getYear() < 1 || requestedMonth.getYear() > 9998
                    || requestedMonth.isAfter(currentMonth)) {
                    throw new IllegalArgumentException("Month is outside the supported range.");
                }
                selectedMonth = requestedMonth;
            } catch (DateTimeParseException | IllegalArgumentException exception) {
                monthError = "Choose a valid month up to and including the current month.";
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("currentMonth", currentMonth.toString());
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("monthError", monthError);
        try {
            model.addAttribute("insights", historyService.getMonthlyInsights(user, selectedMonth));
            model.addAttribute("insightsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("insights", MonthlyTransactionInsights.empty(selectedMonth));
            model.addAttribute("insightsAvailable", false);
        }
        return "insights";
    }

    @GetMapping("/notifications")
    public String notifications(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        try {
            TransactionActivity recent = historyService.getActivity(
                user, TransactionFilter.empty(), 0, RECENT_NOTICE_LIMIT);
            model.addAttribute("notices", recent.transactions());
            Set<Long> readIds = historyService.getReadNotificationIds(user, recent.transactions());
            model.addAttribute("noticeReadIds", readIds);
            model.addAttribute("unreadNoticeCount", recent.transactions().size() - readIds.size());
            model.addAttribute("noticesAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("notices", List.of());
            model.addAttribute("noticeReadIds", Set.of());
            model.addAttribute("unreadNoticeCount", 0);
            model.addAttribute("noticesAvailable", false);
        }
        return "notifications";
    }

    @PostMapping("/notifications/read")
    public String markNotificationsRead(HttpSession session, RedirectAttributes redirectAttributes)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            TransactionActivity recent = historyService.getActivity(
                user, TransactionFilter.empty(), 0, RECENT_NOTICE_LIMIT);
            historyService.markRecentNotificationsRead(user, recent.transactions());
            redirectAttributes.addFlashAttribute("notificationMessage", "All recent updates are marked as read.");
        } catch (SQLException exception) {
            redirectAttributes.addFlashAttribute("notificationError",
                "Updates could not be marked as read right now. Please try again.");
        }
        return "redirect:/notifications";
    }

    @GetMapping("/help")
    public String help(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "help";
    }
}
