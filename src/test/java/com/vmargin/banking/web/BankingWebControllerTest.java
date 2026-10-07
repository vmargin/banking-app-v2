package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.BillPaymentService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.SavedRecipientService;
import com.vmargin.banking.service.SavingsGoalService;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.CardService;
import com.vmargin.banking.model.SavedRecipient;
import com.vmargin.banking.model.SavingsGoal;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {BankingWebController.class, DashboardController.class, CashInController.class,
    TransferController.class, SavingsController.class, TransactionController.class})
@Import({SecurityConfiguration.class, CashInTokens.class, TransactionActivityModelAssembler.class})
class BankingWebControllerTest {
    private static final String CURRENT_ACCOUNT_NUMBER = "123456789012";
    private static final String RECIPIENT_ACCOUNT_NUMBER = "123456789013";
    private static final String OTHER_ACCOUNT_NUMBER = "123456789014";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private LoginService loginService;
    @MockBean
    private RegistrationService registrationService;
    @MockBean
    private CashInService cashInService;
    @MockBean
    private TransferService transferService;
    @MockBean
    private TransactionHistoryService historyService;
    @MockBean
    private SavedRecipientService savedRecipientService;
    @MockBean
    private SavingsGoalService savingsGoalService;
    @MockBean
    private AccountService accountService;
    @MockBean
    private CardService cardService;
    @MockBean
    private SessionAccounts accounts;
    private User current;
    private MockHttpSession session;

    @BeforeEach
    void setup() throws SQLException {
        BankAccount currentAccount = new BankAccount(1, 1, CURRENT_ACCOUNT_NUMBER, "Demo User", "Everyday account",
            "CHECKING", "PHP", "ACTIVE", true, new BigDecimal("1000.00"));
        BankAccount recipientAccount = new BankAccount(2, 2, RECIPIENT_ACCOUNT_NUMBER, "Recipient",
            "Everyday account", "CHECKING", "PHP", "ACTIVE", true, new BigDecimal("500.00"));
        current = new User(1, "09990000001", "REDACTED", "Demo User", currentAccount);
        session = new MockHttpSession();
        session.setAttribute(SessionAccounts.USER_ID, 1L);
        when(accounts.current(any())).thenReturn(current);
        when(accounts.recipient("09990000002")).thenReturn(new User(2, "09990000002", "REDACTED", "Recipient",
            recipientAccount));
        when(accounts.recipient("09990000001")).thenReturn(current);
        when(historyService.getHistory(any())).thenReturn(List.of());
        when(historyService.getActivity(any(), any(), anyInt(), anyInt())).thenReturn(emptyActivity());
        when(historyService.getStatement(any(), any())).thenReturn(List.of());
        when(historyService.getMonthlyInsights(any(), any())).thenReturn(
            MonthlyTransactionInsights.empty(YearMonth.now()));
        when(historyService.getReceipt(any(), anyString())).thenReturn(Optional.empty());
        when(savedRecipientService.list(any())).thenReturn(List.of());
        when(savingsGoalService.list(any())).thenReturn(List.of());
        when(accountService.list(any())).thenReturn(List.of(currentAccount));
        when(accountService.get(current, currentAccount.getId())).thenReturn(Optional.of(currentAccount));
        when(accountService.recipient(RECIPIENT_ACCOUNT_NUMBER)).thenReturn(Optional.of(recipientAccount));
        when(cardService.list(any())).thenReturn(List.of());
    }

    @Test
    void transferPageRendersSavedRecipientActionsAndOwnerScopedList() throws Exception {
        when(savedRecipientService.list(current)).thenReturn(List.of(
            new SavedRecipient(12, RECIPIENT_ACCOUNT_NUMBER, "Family", LocalDateTime.now())));
        mvc.perform(get("/transfer").session(session).with(user("1")))
            .andExpect(status().isOk()).andExpect(view().name("transfer"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Family · " + RECIPIENT_ACCOUNT_NUMBER)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/recipients/12/delete")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"recipientAccountNumber\"")));
    }

    @Test
    void dashboardQuickActionsOpenTheirOwnCustomerFlows() throws Exception {
        String defaultHtml = mvc.perform(get("/dashboard").session(session).with(user("1")))
            .andExpect(status().isOk()).andExpect(view().name("dashboard"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(defaultHtml.contains("Quick actions"));
        assertTrue(defaultHtml.contains("href=\"/transfer\""));
        assertTrue(defaultHtml.contains("href=\"/cash-in\""));
        assertTrue(defaultHtml.contains("href=\"/payments\""));
        assertTrue(defaultHtml.contains("href=\"/savings\""));
    }

    @Test
    void cashInFailureReturnsAnInlineErrorAndEscapedOneRequestValues() throws Exception {
        MvcResult dashboard = mvc.perform(get("/dashboard").session(session).with(user("1")))
            .andExpect(status().isOk()).andReturn();
        String token = (String) dashboard.getModelAndView().getModel().get("cashInToken");
        String details = "Quarterly savings & <notes>";
        when(cashInService.cashIn(eq(current), eq(1L), eq(new BigDecimal("65.40")), eq(details), eq(token)))
            .thenThrow(new SQLException("private-database-diagnostic"));

        mvc.perform(post("/cash-in").session(session).with(user("1")).with(csrf())
            .param("token", token).param("amount", "65.40").param("details", details))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cash-in"));

        MvcResult returned = mvc.perform(get("/cash-in").session(session).with(user("1")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("cashInError",
                "Cash-in could not be recorded. Check the amount and details and try again."))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"65.40\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "value=\"Quarterly savings &amp; &lt;notes&gt;\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-database-diagnostic"))))
            .andReturn();
        assertTrue(returned.getModelAndView().getModel().containsKey("cashInError"));
        MvcResult nextRequest = mvc.perform(get("/cash-in").session(session).with(user("1")))
            .andExpect(status().isOk()).andReturn();
        assertFalse(nextRequest.getModelAndView().getModel().containsKey("cashInError"));
        verify(cashInService).cashIn(eq(current), eq(1L), eq(new BigDecimal("65.40")), eq(details), eq(token));
    }

    @Test
    void transferLookupFailurePreservesAttemptAndHidesDatabaseDetails() throws Exception {
        when(accounts.recipient("09990000002")).thenThrow(new SQLException("private-database-diagnostic"));

        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
                .param("recipient", "09990000002").param("amount", "125.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/transfer"));

        mvc.perform(get("/transfer").session(session)
                .with(user("1")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("transferError", "Recipient lookup is temporarily unavailable."))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"09990000002\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"125.00\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-database-diagnostic"))));
    }

    @Test
    void dashboardRendersNativeAccessibleSavingsProgressWithoutInlineStyle() throws Exception {
        when(savingsGoalService.list(current)).thenReturn(List.of(new SavingsGoal(8, current.getId(), "Trip",
            new BigDecimal("100.00"), new BigDecimal("37.00"), LocalDateTime.now())));

        mvc.perform(get("/dashboard").session(session).with(user("1")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<progress")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"37\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("max=\"100\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Trip progress\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(">37%</progress>")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("style=\"width:37%\""))));
    }

    @Test
    void savedRecipientCanBeSavedRemovedAndReusedByExistingReviewFlow() throws Exception {
        mvc.perform(post("/recipients").session(session).with(user("1")).with(csrf())
                .param("recipientMobile", "09990000002").param("label", "Family"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/transfer#saved-recipients"));
        verify(savedRecipientService).save(eq(current), eq(RECIPIENT_ACCOUNT_NUMBER), eq("Family"));
        mvc.perform(post("/recipients/12/delete").session(session).with(user("1")).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/transfer#saved-recipients"));
        verify(savedRecipientService).delete(current, 12);
        // The selected saved account is copied into the normal recipient field by the transfer page control;
        // it enters the unchanged server-owned transfer-review and confirmation pipeline.
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
                .param("recipient", RECIPIENT_ACCOUNT_NUMBER).param("amount", "25.00"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/transfer/review"));
        mvc.perform(get("/transfer/review").session(session).with(user("1")))
            .andExpect(status().isOk()).andExpect(view().name("transfer-review"))
            .andExpect(model().attribute("recipient", RECIPIENT_ACCOUNT_NUMBER));
    }

    @Test
    void savingsMoveUsesCsrfAndOneUseServerSessionReviewBeforeReceipt() throws Exception {
        SavingsGoal goal = new SavingsGoal(21, current.getId(), "Emergency", new BigDecimal("500.00"),
            new BigDecimal("25.00"), LocalDateTime.now());
        when(savingsGoalService.get(current, goal.id())).thenReturn(goal);
        mvc.perform(post("/savings/review").session(session).with(user("1"))
                .param("goalId", "21").param("action", "CONTRIBUTE").param("amount", "10.00"))
            .andExpect(status().isForbidden());
        verify(savingsGoalService, never()).move(any(), anyLong(), any(), anyBoolean(), anyString());
        mvc.perform(post("/savings/review").session(session).with(user("1")).with(csrf())
                .param("goalId", "21").param("action", "CONTRIBUTE").param("amount", "10.00"))
            .andExpect(redirectedUrl("/savings/review"));
        MvcResult review = mvc.perform(get("/savings/review").session(session).with(user("1")))
            .andExpect(status().isOk()).andExpect(view().name("savings-review"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "rel=\"icon\" type=\"image/svg+xml\" href=\"/favicon.svg\"")))
            .andExpect(model().attribute("pendingSavings", org.hamcrest.Matchers.anything())).andReturn();
        SavingsController.PendingSavings pending = (SavingsController.PendingSavings)
            review.getModelAndView().getModel().get("pendingSavings");
        mvc.perform(post("/savings/confirm").session(session).with(user("1")).with(csrf())
                .param("token", pending.token()))
            .andExpect(redirectedUrl("/transactions/receipt/" + pending.token()));
        verify(savingsGoalService).move(eq(current), eq(21L), eq(new BigDecimal("10.00")),
            eq(true), eq(pending.token()));
        mvc.perform(post("/savings/confirm").session(session).with(user("1")).with(csrf())
                .param("token", pending.token()))
            .andExpect(redirectedUrl("/savings"));
        verify(savingsGoalService, org.mockito.Mockito.times(1))
            .move(any(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void savingsCancelAndExpiredOrWrongTokensDoNotMoveFundsOrConsumeNewerReview() throws Exception {
        SavingsGoal goal = new SavingsGoal(22, current.getId(), "Reserve", new BigDecimal("500.00"),
            new BigDecimal("30.00"), LocalDateTime.now());
        when(savingsGoalService.get(current, goal.id())).thenReturn(goal);
        mvc.perform(post("/savings/review").session(session).with(user("1")).with(csrf())
                .param("goalId", "22").param("action", "WITHDRAW").param("amount", "10.00"))
            .andExpect(redirectedUrl("/savings/review"));
        SavingsController.PendingSavings first = (SavingsController.PendingSavings)
            session.getAttribute("pendingSavingsMove");
        mvc.perform(post("/savings/cancel").session(session).with(user("1")).with(csrf())
                .param("token", first.token())).andExpect(redirectedUrl("/savings"));
        mvc.perform(post("/savings/confirm").session(session).with(user("1")).with(csrf())
                .param("token", first.token())).andExpect(redirectedUrl("/savings"));

        mvc.perform(post("/savings/review").session(session).with(user("1")).with(csrf())
                .param("goalId", "22").param("action", "CONTRIBUTE").param("amount", "12.00"));
        SavingsController.PendingSavings newer = (SavingsController.PendingSavings)
            session.getAttribute("pendingSavingsMove");
        mvc.perform(post("/savings/confirm").session(session).with(user("1")).with(csrf())
                .param("token", first.token())).andExpect(redirectedUrl("/savings"));
        assertSame(newer, session.getAttribute("pendingSavingsMove"));
        var expired = new SavingsController.PendingSavings(first.token(), current.getId(), 22,
            "Reserve", true, new BigDecimal("10.00"), Instant.EPOCH);
        session.setAttribute("pendingSavingsMove", expired);
        mvc.perform(post("/savings/cancel").session(session).with(user("1")).with(csrf())
                .param("token", first.token())).andExpect(redirectedUrl("/savings"));
        assertSame(expired, session.getAttribute("pendingSavingsMove"));
        session.setAttribute("pendingSavingsMove", newer);
        verify(savingsGoalService, never()).move(any(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void publicLoginHasCsrfFieldsAndSecurityHeaders() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void activityPageRendersSavedFiltersSignedRowsReceiptLinksAndPagination() throws Exception {
        String reference = UUID.randomUUID().toString();
        TransactionFilter filter = new TransactionFilter(TransactionType.TRANSFER_SENT,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "rent");
        Transaction row = new Transaction(9, current.getId(), 1, "Everyday account", "PHP",
            TransactionType.TRANSFER_SENT, new BigDecimal("30.00"), "Rent",
            LocalDateTime.of(2026, 9, 2, 10, 30), reference);
        TransactionActivity activity = new TransactionActivity(List.of(row), filter, 0, 10,
            12, 19, new BigDecimal("20.00"), new BigDecimal("30.00"));
        when(historyService.getActivity(eq(current), any(), eq(0), eq(10))).thenReturn(activity);

        mvc.perform(get("/activity").session(session).with(user("1"))
            .param("userId", "999").param("type", "TRANSFER_SENT")
            .param("fromDate", "2026-09-01").param("toDate", "2026-09-02")
            .param("search", "rent").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(view().name("activity"))
            .andExpect(model().attribute("activityAvailable", true))
            .andExpect(model().attribute("activity", activity))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("12 entries")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 1 of 2")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"rent\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("− ₱30.00")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "/transactions/receipt/" + reference)));

        verify(historyService).getActivity(eq(current), eq(filter), eq(0), eq(10));
    }

    @Test
    void foreignReceiptReferenceReturnsNotFoundWithoutReceiptContent() throws Exception {
        String reference = UUID.randomUUID().toString();

        mvc.perform(get("/transactions/receipt/" + reference).session(session).with(user("1")))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));

        verify(historyService).getReceipt(current, reference);
    }

    @Test
    void ownReceiptRendersOnlyTheAuthenticatedLedgerRecord() throws Exception {
        String reference = UUID.randomUUID().toString();
        Transaction receipt = new Transaction(9, current.getId(), TransactionType.TRANSFER_RECEIVED,
            new BigDecimal("40.00"), "Transfer from 09990000002",
            LocalDateTime.of(2026, 9, 2, 10, 30), reference);
        when(historyService.getReceipt(current, reference)).thenReturn(Optional.of(receipt));

        mvc.perform(get("/transactions/receipt/" + reference).session(session).with(user("1")))
            .andExpect(status().isOk())
            .andExpect(view().name("receipt"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("+ ₱40.00")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(reference)))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("name=\"userId\""))));
    }

    @Test
    void csvUsesTheSameTypedFiltersAndNeutralizesUntrustedText() throws Exception {
        TransactionFilter filter = new TransactionFilter(TransactionType.CASH_IN,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "rent");
        Transaction row = new Transaction(9, current.getId(), TransactionType.CASH_IN,
            new BigDecimal("50.00"), "=HYPERLINK(\"bad\")", LocalDateTime.of(2026, 9, 2, 10, 30),
            UUID.randomUUID().toString());
        when(historyService.getStatement(current, filter)).thenReturn(List.of(row));

        mvc.perform(get("/statement.csv").session(session).with(user("1"))
            .param("type", "CASH_IN").param("fromDate", "2026-09-01")
            .param("toDate", "2026-09-02").param("search", "rent"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"cash-g-statement.csv\""))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"'=HYPERLINK(")));

        verify(historyService).getStatement(current, filter);
    }

    @Test
    void invalidActivityFilterShowsAnErrorWithoutQueryingHistory() throws Exception {
        mvc.perform(get("/dashboard").session(session).with(user("1")).param("type", "UNKNOWN"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("activityAvailable", false))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Check the activity filters")));
        verify(historyService, never()).getActivity(any(), any(), anyInt(), anyInt());
    }

    @Test
    void outOfRangeDateFilterShowsAnErrorWithoutQueryingHistory() throws Exception {
        mvc.perform(get("/dashboard").session(session).with(user("1")).param("fromDate", "9999-01-01"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("activityAvailable", false))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Check the activity filters")));
        verify(historyService, never()).getActivity(any(), any(), anyInt(), anyInt());
    }

    @Test
    void historyFailureShowsUnavailableStateWithoutDatabaseDiagnostics() throws Exception {
        when(historyService.getActivity(any(), any(), anyInt(), anyInt()))
            .thenThrow(new SQLException("private-database-diagnostic"));

        mvc.perform(get("/dashboard").session(session).with(user("1")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("activityAvailable", false))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Activity is unavailable")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-database-diagnostic"))));
    }

    @Test
    void exportDatabaseFailureReturnsGenericUnavailableResponse() throws Exception {
        when(historyService.getStatement(any(), any()))
            .thenThrow(new SQLException("private-database-diagnostic"));

        mvc.perform(get("/statement.csv").session(session).with(user("1")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string("Statement export is temporarily unavailable."))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-database-diagnostic"))));
    }

    @Test
    void unauthorizedRoutesRedirectAndMissingCsrfNeverWrites() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").param("mobile", "09990000001").param("pin", "1234"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cash-in").with(user("1")).session(session)
            .param("amount", "100").param("details", "test")).andExpect(status().isForbidden());
        mvc.perform(post("/cash-in").with(user("1")).with(csrf().useInvalidToken()).session(session)
            .param("amount", "100").param("details", "test")).andExpect(status().isForbidden());
        verify(cashInService, never()).cashIn(any(), any(), anyString(), anyString());
    }

    @Test
    void loginRotatesSessionAndInvalidatesPendingBillReview() throws Exception {
        User authenticated = new User(current.getId(), current.getMobileNumber(),
            com.vmargin.banking.util.PinHasher.hash("1234"), current.getFullName(), current.getBankAccount());
        when(loginService.login(anyString(), anyString())).thenReturn(authenticated);
        session.setAttribute(BillPaymentController.PENDING_ATTRIBUTE, new BillPaymentController.PendingBillPayment(
            "old-bill-review", current.getId(), current.getBankAccount().getId(),
            current.getBankAccount().getAccountName(), new BillPaymentService.PreparedPayment(
                DemoBiller.WATER, "9012", new BigDecimal("20.00")),
            Instant.now().plusSeconds(300)));
        String oldId = session.getId();
        mvc.perform(post("/login").session(session).with(csrf())
            .param("mobile", "09990000001").param("pin", "1234"))
            .andExpect(redirectedUrl("/dashboard"));
        assertNotEquals(oldId, session.getId());
        assertEquals(1L, session.getAttribute(SessionAccounts.USER_ID));
        assertNotEquals(authenticated.getPinForPersistence(),
            session.getAttribute(SessionAccounts.CREDENTIAL_FINGERPRINT));
        assertNull(session.getAttribute("authenticatedUser"));
        assertNull(session.getAttribute(BillPaymentController.PENDING_ATTRIBUTE));
        mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    void loginDoesNotExposeDatabaseErrors() throws Exception {
        when(loginService.login(anyString(), anyString())).thenThrow(new SQLException("secret-db-diagnostic"));
        mvc.perform(post("/login").with(csrf()).param("mobile", "09990000001").param("pin", "1234"))
            .andExpect(view().name("login"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("secret-db-diagnostic"))));
    }

    @Test
    void transferTokenConsumedOnceAndInvalidReviewClearsPriorRequest() throws Exception {
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
            .param("recipient", RECIPIENT_ACCOUNT_NUMBER).param("amount", "100.00"))
            .andExpect(redirectedUrl("/transfer/review"));
        MvcResult result = mvc.perform(get("/transfer/review").session(session).with(user("1")))
            .andExpect(model().attribute("recipient", RECIPIENT_ACCOUNT_NUMBER))
            .andExpect(model().attribute("recipientName", "Recipient"))
            .andExpect(model().attribute("transferFee", new BigDecimal("0.00")))
            .andExpect(model().attribute("totalDebit", new BigDecimal("100.00")))
            .andExpect(model().attribute("remainingBalance", new BigDecimal("900.00"))).andReturn();
        String reviewHtml = result.getResponse().getContentAsString();
        assertTrue(reviewHtml.contains("<dt>To</dt>") && reviewHtml.contains("Recipient")
            && reviewHtml.contains(RECIPIENT_ACCOUNT_NUMBER));
        assertTrue(reviewHtml.contains("Transfer fee") && reviewHtml.contains("₱0.00"));
        assertTrue(reviewHtml.contains("Total debit") && reviewHtml.contains("₱100.00"));
        String token = (String) result.getModelAndView().getModel().get("operationToken");
        mvc.perform(post("/transfer/confirm").session(session).with(user("1")).with(csrf()).param("token", token))
            .andExpect(redirectedUrl("/transactions/receipt/" + token));
        mvc.perform(post("/transfer/confirm").session(session).with(user("1")).with(csrf()).param("token", token))
            .andExpect(redirectedUrl("/transfer"));
        verify(transferService).transferToAccount(current, 1, RECIPIENT_ACCOUNT_NUMBER,
            new BigDecimal("100.00"), token);
        assertNull(session.getAttribute("pendingTransfer"));
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
            .param("recipient", RECIPIENT_ACCOUNT_NUMBER).param("amount", "100.00"));
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
            .param("recipient", "09990000001").param("amount", "100.00"));
        assertNull(session.getAttribute("pendingTransfer"));
    }

    @Test
    void wrongTabTokenRejectsAndCashInReplaysNeverWriteTwice() throws Exception {
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
            .param("recipient", RECIPIENT_ACCOUNT_NUMBER).param("amount", "100.00"));
        mvc.perform(post("/transfer/confirm").session(session).with(user("1")).with(csrf()).param("token", "wrong"));
        verify(transferService, never()).transferToAccount(any(), anyLong(), anyString(), any(), anyString());
        MvcResult page = mvc.perform(get("/dashboard").session(session).with(user("1"))).andReturn();
        String token = (String) page.getModelAndView().getModel().get("cashInToken");
        for (int count = 0; count < 2; count++) {
            mvc.perform(post("/cash-in").session(session).with(user("1")).with(csrf())
                .param("token", token).param("amount", "50.00").param("details", "Demo funds"));
        }
        verify(cashInService).cashIn(eq(current), eq(1L), eq(new BigDecimal("50.00")), eq("Demo funds"), eq(token));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        mvc.perform(post("/logout").session(session).with(user("1")).with(csrf()))
            .andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }

    @Test
    void staleConfirmAndCancelTokensPreserveNewerReviewAndConfirmOnlyItsAmount() throws Exception {
        String tokenA = openReview("100.00");
        String tokenB = openReview("250.00");
        Object reviewB = session.getAttribute("pendingTransfer");
        assertNotEquals(tokenA, tokenB);

        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/transfer/" + action).session(session).with(user("1")).with(csrf())
                .param("token", tokenA)).andExpect(redirectedUrl("/transfer"));
            assertSame(reviewB, session.getAttribute("pendingTransfer"));
        }
        verify(transferService, never()).transferToAccount(any(), anyLong(), anyString(), any(), anyString());
        mvc.perform(get("/transfer/review").session(session).with(user("1")))
            .andExpect(model().attribute("operationToken", tokenB))
            .andExpect(model().attribute("amount", new BigDecimal("250.00")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "action=\"/transfer/cancel\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "name=\"token\" value=\"" + tokenB + "\"")));
        mvc.perform(post("/transfer/confirm").session(session).with(user("1")).with(csrf())
            .param("token", tokenB)).andExpect(redirectedUrl("/transactions/receipt/" + tokenB));
        verify(transferService).transferToAccount(current, 1, RECIPIENT_ACCOUNT_NUMBER,
            new BigDecimal("250.00"), tokenB);
        assertNull(session.getAttribute("pendingTransfer"));
    }

    @Test
    void currentCancelTokenClearsItsReviewWithoutExecutingTransfer() throws Exception {
        String token = openReview("100.00");
        mvc.perform(post("/transfer/cancel").session(session).with(user("1")).with(csrf())
            .param("token", token)).andExpect(redirectedUrl("/transfer"));
        assertNull(session.getAttribute("pendingTransfer"));
        verify(transferService, never()).transferToAccount(any(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void wrongOwnerCannotConfirmOrCancelEvenWithTheExactCurrentToken() throws Exception {
        String token = openReview("100.00");
        Object review = session.getAttribute("pendingTransfer");
        User another = new User(2, "09990000002", "REDACTED", "Other owner",
            new BankAccount(3, 2, OTHER_ACCOUNT_NUMBER, "Other owner", "Everyday account", "CHECKING", "PHP",
                "ACTIVE", true, new BigDecimal("500.00")));
        when(accounts.current(any())).thenReturn(another);
        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/transfer/" + action).session(session).with(user("2")).with(csrf())
                .param("token", token)).andExpect(redirectedUrl("/transfer"));
            assertSame(review, session.getAttribute("pendingTransfer"));
        }
        verify(transferService, never()).transferToAccount(any(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void expiredReviewCannotBeConfirmedOrCanceledAndDoesNotConsumeNewerReview() throws Exception {
        String tokenA = openReview("100.00");
        var expired = new TransferController.PendingTransfer(tokenA, current.getId(),
            current.getBankAccount().getId(), current.getBankAccount().getAccountName(),
            "09990000002", "Recipient", new BigDecimal("100.00"), Instant.EPOCH);
        session.setAttribute("pendingTransfer", expired);
        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/transfer/" + action).session(session).with(user("1")).with(csrf())
                .param("token", tokenA)).andExpect(redirectedUrl("/transfer"));
            assertSame(expired, session.getAttribute("pendingTransfer"));
        }
        String tokenB = openReview("250.00");
        Object reviewB = session.getAttribute("pendingTransfer");
        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/transfer/" + action).session(session).with(user("1")).with(csrf())
                .param("token", tokenA)).andExpect(redirectedUrl("/transfer"));
            assertSame(reviewB, session.getAttribute("pendingTransfer"));
        }
        assertNotEquals(tokenA, tokenB);
        verify(transferService, never()).transferToAccount(any(), anyLong(), anyString(), any(), anyString());
    }

    private String openReview(String amount) throws Exception {
        mvc.perform(post("/transfer/review").session(session).with(user("1")).with(csrf())
            .param("recipient", RECIPIENT_ACCOUNT_NUMBER).param("amount", amount))
            .andExpect(redirectedUrl("/transfer/review"));
        MvcResult review = mvc.perform(get("/transfer/review").session(session).with(user("1")))
            .andExpect(status().isOk()).andReturn();
        return (String) review.getModelAndView().getModel().get("operationToken");
    }

    private TransactionActivity emptyActivity() {
        return new TransactionActivity(List.of(), TransactionFilter.empty(), 0, 10, 0, 0,
            new BigDecimal("0.00"), new BigDecimal("0.00"));
    }
}
