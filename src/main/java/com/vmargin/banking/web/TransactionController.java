package com.vmargin.banking.web;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.StatementCsvExporter;
import com.vmargin.banking.service.TransactionHistoryService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Owns authenticated activity, receipt, and downloadable statement routes. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TransactionController {
    private final SessionAccounts accounts;
    private final AccountService accountService;
    private final TransactionHistoryService historyService;
    private final TransactionActivityModelAssembler activityModel;

    public TransactionController(SessionAccounts accounts, AccountService accountService,
                                 TransactionHistoryService historyService,
                                 TransactionActivityModelAssembler activityModel) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.historyService = historyService;
        this.activityModel = activityModel;
    }

    @GetMapping("/activity")
    public String activityPage(HttpSession session, Model model,
                               @RequestParam(required = false) String type,
                               @RequestParam(required = false) String fromDate,
                               @RequestParam(required = false) String toDate,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String accountId,
                               @RequestParam(required = false) String page,
                               @RequestParam(required = false) String pageSize) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        try {
            model.addAttribute("bankAccounts", accountService.list(user));
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("bankAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        activityModel.populate(user, model, type, fromDate, toDate, search, accountId, page, pageSize);
        SessionFlash.consumeActions(session, model);
        return "activity";
    }

    @GetMapping("/transactions/receipt/{reference}")
    public Object transactionReceipt(@PathVariable String reference, HttpSession session, Model model) {
        try {
            User user = accounts.current(session);
            if (user == null) {
                return "redirect:/login";
            }
            Optional<Transaction> receipt = historyService.getReceipt(user, reference);
            if (receipt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            model.addAttribute("user", user);
            model.addAttribute("transaction", receipt.orElseThrow());
            return "receipt";
        } catch (SQLException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Receipt details are temporarily unavailable.");
        }
    }

    @GetMapping("/statement.csv")
    public ResponseEntity<String> exportStatement(HttpSession session,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String fromDate,
                                                  @RequestParam(required = false) String toDate,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(required = false) String accountId) {
        try {
            User user = accounts.current(session);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sign in to export account activity.");
            }
            var filter = activityModel.parseFilter(user, type, fromDate, toDate, search, accountId);
            String csv = new StatementCsvExporter().export(historyService.getStatement(user, filter));
            return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cash-g-statement.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body("Check the statement filters and try again.");
        } catch (SQLException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Statement export is temporarily unavailable.");
        }
    }
}
