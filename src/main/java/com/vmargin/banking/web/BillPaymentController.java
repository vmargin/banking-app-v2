package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.BillPaymentService;
import com.vmargin.banking.service.BillPaymentService.PreparedPayment;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BillPaymentController {
    static final String PENDING_ATTRIBUTE = "pendingBillPayment";
    private static final String ERROR = "actionError";
    private static final String SUCCESS = "actionSuccess";
    private static final String BILL_PAYMENT_DASHBOARD = "/payments";
    private final BillPaymentService billPayments;
    private final SessionAccounts accounts;
    private final AccountService accountService;

    public BillPaymentController(BillPaymentService billPayments, SessionAccounts accounts,
                                 AccountService accountService) {
        this.billPayments = billPayments;
        this.accounts = accounts;
        this.accountService = accountService;
    }

    @GetMapping("/payments")
    public String payments(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("demoBillers", DemoBiller.values());
        try {
            model.addAttribute("eligibleAccounts", accountService.list(user).stream()
                .filter(account -> "ACTIVE".equals(account.getStatus())
                    && "PHP".equals(account.getCurrencyCode())).toList());
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("eligibleAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        Object error = session.getAttribute(ERROR);
        Object success = session.getAttribute(SUCCESS);
        if (error instanceof String message) {
            model.addAttribute("error", message);
            session.removeAttribute(ERROR);
        }
        if (success instanceof String message) {
            model.addAttribute("success", message);
            session.removeAttribute(SUCCESS);
        }
        return "payments";
    }

    @PostMapping("/bills/review")
    public String startReview(@RequestParam String biller, @RequestParam String billReference,
                              @RequestParam String amount,
                              @RequestParam(defaultValue = "0") long accountId,
                              HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            try {
                BankAccount source = accountId < 1 ? user.getBankAccount()
                    : accountService.get(user, accountId).orElseThrow(
                        () -> new IllegalArgumentException("Choose an account on your profile."));
                if (!"PHP".equals(source.getCurrencyCode()) || !"ACTIVE".equals(source.getStatus())) {
                    throw new IllegalArgumentException("Choose an active PHP account for this demo payment.");
                }
                PreparedPayment payment = billPayments.prepare(biller, billReference, amount);
                if (payment.amount().compareTo(source.getBalance()) > 0) {
                    throw new IllegalArgumentException("Payment amount exceeds your available balance.");
                }
                session.setAttribute(PENDING_ATTRIBUTE, new PendingBillPayment(
                    UUID.randomUUID().toString(), user.getId(), source.getId(), source.getAccountName(),
                    payment, Instant.now().plusSeconds(300)));
                return "redirect:/bills/review";
            } catch (IllegalArgumentException exception) {
                session.setAttribute(ERROR, exception.getMessage());
            }
        }
        return "redirect:" + BILL_PAYMENT_DASHBOARD;
    }

    @GetMapping("/bills/review")
    public String review(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingBillPayment pending;
        synchronized (session) {
            pending = pending(session);
            if (!isActive(pending, user)) {
                if (pending != null && pending.ownerId() == user.getId()) {
                    session.removeAttribute(PENDING_ATTRIBUTE);
                }
                session.setAttribute(ERROR, "The demo bill-payment review expired. Start again.");
                return "redirect:" + BILL_PAYMENT_DASHBOARD;
            }
        }
        model.addAttribute("user", user);
        model.addAttribute("pendingBillPayment", pending);
        model.addAttribute("accountName", pending.accountName());
        return "bill-payment-review";
    }

    @PostMapping("/bills/confirm")
    public String confirm(@RequestParam(defaultValue = "") String token, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingBillPayment pending;
        synchronized (session) {
            pending = pending(session);
            if (matches(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            } else {
                pending = null;
            }
        }
        if (pending == null) {
            session.setAttribute(ERROR, "This demo payment review expired or was already used. Start again.");
            return "redirect:" + BILL_PAYMENT_DASHBOARD;
        }
        try {
            billPayments.pay(user, pending.accountId(), pending.payment(), pending.token());
            session.setAttribute(SUCCESS, "Simulated bill payment recorded locally. No provider was contacted.");
            return "redirect:/transactions/receipt/" + pending.token();
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute(ERROR, "Demo bill payment could not be recorded. "
                + "Check available funds and start again.");
            return "redirect:" + BILL_PAYMENT_DASHBOARD;
        }
    }

    @PostMapping("/bills/cancel")
    public String cancel(@RequestParam(defaultValue = "") String token, HttpSession session) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            PendingBillPayment pending = pending(session);
            if (matches(pending, user, token)) {
                session.removeAttribute(PENDING_ATTRIBUTE);
            }
        }
        return "redirect:" + BILL_PAYMENT_DASHBOARD;
    }

    private PendingBillPayment pending(HttpSession session) {
        Object value = session.getAttribute(PENDING_ATTRIBUTE);
        return value instanceof PendingBillPayment pending ? pending : null;
    }

    private boolean isActive(PendingBillPayment pending, User user) {
        return pending != null && pending.ownerId() == user.getId() && pending.expires().isAfter(Instant.now());
    }

    private boolean matches(PendingBillPayment pending, User user, String token) {
        return isActive(pending, user) && pending.token().equals(token);
    }

    record PendingBillPayment(String token, long ownerId, long accountId, String accountName,
                              PreparedPayment payment, Instant expires) {
    }
}
