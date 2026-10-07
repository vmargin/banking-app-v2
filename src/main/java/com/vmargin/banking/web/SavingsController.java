package com.vmargin.banking.web;

import com.vmargin.banking.model.SavingsGoal;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.SavingsGoalService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owns savings goals and the reviewed reserve-movement flow. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SavingsController {
    static final String PENDING_ATTRIBUTE = "pendingSavingsMove";
    private static final int REVIEW_SECONDS = 300;

    private final SavingsGoalService savingsGoals;
    private final SessionAccounts accounts;

    public SavingsController(SavingsGoalService savingsGoals, SessionAccounts accounts) {
        this.savingsGoals = savingsGoals;
        this.accounts = accounts;
    }

    @GetMapping("/savings")
    public String savingsPage(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        try {
            model.addAttribute("savingsGoals", savingsGoals.list(user));
            model.addAttribute("savingsGoalsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("savingsGoals", List.of());
            model.addAttribute("savingsGoalsAvailable", false);
        }
        SessionFlash.consumeActions(session, model);
        return "savings";
    }

    @PostMapping("/savings-goals")
    public String createSavingsGoal(@RequestParam String name, @RequestParam String targetAmount,
                                    HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            SavingsGoal goal = savingsGoals.create(user, name, targetAmount);
            session.setAttribute(SessionFlash.SUCCESS_ATTRIBUTE, "Savings goal created: " + goal.name());
        } catch (IllegalArgumentException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "Savings goal could not be created. Try again.");
        }
        return "redirect:/savings";
    }

    @PostMapping("/savings/review")
    public String startSavingsReview(@RequestParam long goalId, @RequestParam String action,
                                     @RequestParam String amount, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            try {
                boolean contribution = "CONTRIBUTE".equals(action);
                if (!contribution && !"WITHDRAW".equals(action)) {
                    throw new IllegalArgumentException("Choose a valid savings action.");
                }
                SavingsGoal goal = savingsGoals.get(user, goalId);
                if (goal == null) {
                    throw new IllegalArgumentException("Savings goal was not found on your account.");
                }
                BigDecimal value = MoneyValidation.parse(amount);
                BigDecimal available = contribution ? user.getBalance() : goal.savedAmount();
                if (value.compareTo(available) > 0) {
                    throw new IllegalArgumentException(contribution ? "Amount exceeds your available balance."
                        : "Amount exceeds funds saved in this goal.");
                }
                PendingSavings pending = new PendingSavings(UUID.randomUUID().toString(), user.getId(),
                    goal.id(), goal.name(), contribution, value, Instant.now().plusSeconds(REVIEW_SECONDS));
                session.setAttribute(PENDING_ATTRIBUTE, pending);
                return "redirect:/savings/review";
            } catch (IllegalArgumentException exception) {
                session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, exception.getMessage());
            } catch (SQLException exception) {
                session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "Savings goal could not be loaded. Try again.");
            }
        }
        return "redirect:/savings";
    }

    @GetMapping("/savings/review")
    public String savingsReview(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingSavings pending;
        synchronized (session) {
            pending = pending(session);
            if (pending == null || pending.ownerId() != user.getId() || !pending.expires().isAfter(Instant.now())) {
                if (pending != null && pending.ownerId() == user.getId()) {
                    session.removeAttribute(PENDING_ATTRIBUTE);
                }
                session.setAttribute(SessionFlash.ERROR_ATTRIBUTE, "The savings review expired. Start again.");
                return "redirect:/savings";
            }
        }
        model.addAttribute("user", user);
        model.addAttribute("pendingSavings", pending);
        return "savings-review";
    }

    @PostMapping("/savings/confirm")
    public String confirmSavings(@RequestParam(defaultValue = "") String token, HttpSession session)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingSavings pending;
        synchronized (session) {
            pending = pending(session);
            if (matchesSavings(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            } else {
                pending = null;
            }
        }
        if (pending == null) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                "This savings review expired or was already used. Start again.");
            return "redirect:/savings";
        }
        try {
            savingsGoals.move(user, pending.goalId(), pending.amount(), pending.contribution(), pending.token());
            return "redirect:/transactions/receipt/" + pending.token();
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute(SessionFlash.ERROR_ATTRIBUTE,
                "Savings move could not be completed. Check available funds and start again.");
            return "redirect:/savings";
        }
    }

    @PostMapping("/savings/cancel")
    public String cancelSavings(@RequestParam(defaultValue = "") String token, HttpSession session)
        throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            PendingSavings pending = pending(session);
            if (matchesSavings(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            }
        }
        return "redirect:/savings";
    }

    private PendingSavings pending(HttpSession session) {
        Object value = session.getAttribute(PENDING_ATTRIBUTE);
        return value instanceof PendingSavings savings ? savings : null;
    }

    private boolean matchesSavings(PendingSavings pending, User user, String token) {
        return pending != null && pending.ownerId() == user.getId() && pending.token().equals(token)
            && pending.expires().isAfter(Instant.now());
    }

    record PendingSavings(String token, long ownerId, long goalId, String goalName,
                          boolean contribution, BigDecimal amount, Instant expires) {
    }
}
