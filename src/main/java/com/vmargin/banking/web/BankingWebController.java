package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
public class BankingWebController {

    private static final String USER_SESSION_KEY = "authenticatedUser";
    private static final String ACTION_ERROR_SESSION_KEY = "actionError";
    private static final String ACTION_SUCCESS_SESSION_KEY = "actionSuccess";
    private static final String PENDING_TRANSFER_SESSION_KEY = "pendingTransfer";
    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final CashInService cashInService;
    private final TransferService transferService;
    private final TransactionHistoryService historyService;

    public BankingWebController(
        LoginService loginService,
        RegistrationService registrationService,
        CashInService cashInService,
        TransferService transferService,
        TransactionHistoryService historyService
    ) {
        this.loginService = loginService;
        this.registrationService = registrationService;
        this.cashInService = cashInService;
        this.transferService = transferService;
        this.historyService = historyService;
    }

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam String mobile,
        @RequestParam String pin,
        HttpSession session,
        Model model
    ) {
        try {
            session.setAttribute(USER_SESSION_KEY, loginService.login(mobile, pin));
            return "redirect:/dashboard";
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
        @RequestParam String fullName,
        @RequestParam String mobile,
        @RequestParam String pin,
        Model model
    ) {
        try {
            registrationService.register(fullName, mobile, pin);
            model.addAttribute("success", "Account created. You can sign in now.");
            return "login";
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", exception.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        Object actionError = session.getAttribute(ACTION_ERROR_SESSION_KEY);
        if (actionError instanceof String message) {
            model.addAttribute("error", message);
            session.removeAttribute(ACTION_ERROR_SESSION_KEY);
        }
        Object actionSuccess = session.getAttribute(ACTION_SUCCESS_SESSION_KEY);
        if (actionSuccess instanceof String message) {
            model.addAttribute("success", message);
            session.removeAttribute(ACTION_SUCCESS_SESSION_KEY);
        }
        try {
            model.addAttribute("transactions", historyService.getHistory(user));
        } catch (SQLException exception) {
            model.addAttribute("error", "Transaction history is temporarily unavailable.");
        }
        return "dashboard";
    }

    @PostMapping("/cash-in")
    public String cashIn(
        @RequestParam BigDecimal amount,
        @RequestParam String details,
        HttpSession session
    ) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            cashInService.cashIn(user, amount, details);
            session.setAttribute(ACTION_SUCCESS_SESSION_KEY, "Funds were recorded in your ledger.");
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute(ACTION_ERROR_SESSION_KEY, exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transfer/review")
    public String startTransferReview(
        @RequestParam String recipient,
        @RequestParam String amount,
        HttpSession session
    ) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            session.setAttribute(
                PENDING_TRANSFER_SESSION_KEY,
                createPendingTransfer(user, recipient, amount)
            );
            return "redirect:/transfer/review";
        } catch (RuntimeException exception) {
            session.setAttribute(ACTION_ERROR_SESSION_KEY, exception.getMessage());
            return "redirect:/dashboard";
        }
    }

    @GetMapping("/transfer/review")
    public String transferReview(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingTransfer pendingTransfer = pendingTransfer(session);
        if (pendingTransfer == null) {
            session.setAttribute(
                ACTION_ERROR_SESSION_KEY,
                "Start a transfer before opening its review record."
            );
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        model.addAttribute("recipient", pendingTransfer.recipient());
        model.addAttribute("amount", pendingTransfer.amount());
        model.addAttribute(
            "remainingBalance",
            user.getBalance().subtract(pendingTransfer.amount())
        );
        return "transfer-review";
    }

    @PostMapping("/transfer/confirm")
    public String confirmTransfer(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        PendingTransfer pendingTransfer = pendingTransfer(session);
        if (pendingTransfer == null) {
            session.setAttribute(
                ACTION_ERROR_SESSION_KEY,
                "The transfer record is no longer available. Start again from your account desk."
            );
            return "redirect:/dashboard";
        }
        try {
            transferService.transfer(user, pendingTransfer.recipient(), pendingTransfer.amount());
            session.setAttribute(ACTION_SUCCESS_SESSION_KEY, "Transfer recorded in your ledger.");
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute(ACTION_ERROR_SESSION_KEY, exception.getMessage());
        } finally {
            session.removeAttribute(PENDING_TRANSFER_SESSION_KEY);
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transfer/cancel")
    public String cancelTransferReview(HttpSession session) {
        session.removeAttribute(PENDING_TRANSFER_SESSION_KEY);
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private User currentUser(HttpSession session) {
        Object user = session.getAttribute(USER_SESSION_KEY);
        return user instanceof User authenticatedUser ? authenticatedUser : null;
    }

    private PendingTransfer pendingTransfer(HttpSession session) {
        Object pendingTransfer = session.getAttribute(PENDING_TRANSFER_SESSION_KEY);
        return pendingTransfer instanceof PendingTransfer request ? request : null;
    }

    private PendingTransfer createPendingTransfer(User user, String recipient, String rawAmount) {
        String normalizedRecipient = recipient == null ? "" : recipient.trim();
        if (normalizedRecipient.isBlank()) {
            throw new IllegalArgumentException("Recipient mobile number is required.");
        }
        if (user.getMobileNumber().equals(normalizedRecipient)) {
            throw new IllegalArgumentException("You cannot transfer to your own account.");
        }
        BigDecimal amount = parseTransferAmount(rawAmount);
        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Transfer amount exceeds your available balance.");
        }
        return new PendingTransfer(normalizedRecipient, amount);
    }

    private BigDecimal parseTransferAmount(String rawAmount) {
        try {
            BigDecimal amount = new BigDecimal(rawAmount);
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) {
                throw new IllegalArgumentException(
                    "Transfer amount must be positive and use no more than two decimals."
                );
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid transfer amount.");
        }
    }

    private record PendingTransfer(String recipient, BigDecimal amount) {
    }
}
