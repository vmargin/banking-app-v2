package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.TransactionHistoryService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AccountInformationController.class)
@Import(SecurityConfiguration.class)
class AccountInformationControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private SessionAccounts accounts;
    @MockBean
    private TransactionHistoryService historyService;
    private User current;
    private MockHttpSession session;

    @BeforeEach
    void setup() throws Exception {
        current = new User(1, "09990000001", "REDACTED", "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1000.00")));
        session = new MockHttpSession();
        session.setAttribute(SessionAccounts.USER_ID, 1L);
        when(accounts.current(any())).thenReturn(current);
    }

    @Test
    void insightsRenderOwnerScopedLedgerGroupsAndKeepSavingsSeparateFromCashFlow() throws Exception {
        YearMonth month = YearMonth.of(2026, 9);
        MonthlyTransactionInsights insights = new MonthlyTransactionInsights(month, List.of(
            new MonthlyTransactionInsights.TypeTotal(TransactionType.CASH_IN, 1, new BigDecimal("80.00")),
            new MonthlyTransactionInsights.TypeTotal(TransactionType.TRANSFER_RECEIVED, 1,
                new BigDecimal("20.00")),
            new MonthlyTransactionInsights.TypeTotal(TransactionType.TRANSFER_SENT, 1, new BigDecimal("25.00")),
            new MonthlyTransactionInsights.TypeTotal(TransactionType.BILL_PAYMENT, 1, new BigDecimal("8.00")),
            new MonthlyTransactionInsights.TypeTotal(TransactionType.SAVINGS_CONTRIBUTION, 1,
                new BigDecimal("30.00")),
            new MonthlyTransactionInsights.TypeTotal(TransactionType.SAVINGS_WITHDRAWAL, 1,
                new BigDecimal("10.00"))));
        when(historyService.getMonthlyInsights(current, month)).thenReturn(insights);

        mvc.perform(get("/insights").with(user("1")).session(session).param("month", "2026-09"))
            .andExpect(status().isOk())
            .andExpect(view().name("insights"))
            .andExpect(model().attribute("insights", insights))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Money in")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("₱100.00")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("₱8.00")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("₱30.00")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Savings withdrawal"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Moved to goals")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Account transfers and goal movements are tracked separately from spending.")));

        verify(historyService).getMonthlyInsights(current, month);
    }

    @Test
    void invalidMonthShowsErrorAndUsesCurrentMonth() throws Exception {
        YearMonth currentMonth = YearMonth.now();
        when(historyService.getMonthlyInsights(current, currentMonth))
            .thenReturn(MonthlyTransactionInsights.empty(currentMonth));

        mvc.perform(get("/insights").with(user("1")).session(session).param("month", "not-a-month"))
            .andExpect(status().isOk())
            .andExpect(view().name("insights"))
            .andExpect(model().attribute("monthError",
                "Choose a valid month up to and including the current month."))
            .andExpect(model().attribute("selectedMonth", currentMonth.toString()));
    }

    @Test
    void insightsShowReadOnlyUnavailableStateWhenLedgerQueryFails() throws Exception {
        YearMonth month = YearMonth.of(2026, 9);
        doThrow(new SQLException("private-ledger-diagnostic"))
            .when(historyService).getMonthlyInsights(current, month);

        mvc.perform(get("/insights").with(user("1")).session(session).param("month", "2026-09"))
            .andExpect(status().isOk())
            .andExpect(view().name("insights"))
            .andExpect(model().attribute("insightsAvailable", false))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Insights are unavailable.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-ledger-diagnostic"))));
    }

    @Test
    void noticesComeFromSavedActivityAndStateThatNoExternalAlertsAreSent() throws Exception {
        String reference = UUID.randomUUID().toString();
        Transaction notice = new Transaction(9, current.getId(), TransactionType.TRANSFER_SENT,
            new BigDecimal("12.50"), "Practice transfer", LocalDateTime.of(2026, 9, 1, 12, 0), reference);
        TransactionActivity recent = new TransactionActivity(List.of(notice), TransactionFilter.empty(), 0, 10,
            1, 1, BigDecimal.ZERO.setScale(2), new BigDecimal("12.50"));
        when(historyService.getActivity(eq(current), any(), eq(0), eq(10))).thenReturn(recent);
        when(historyService.getReadNotificationIds(current, recent.transactions())).thenReturn(Set.of());

        mvc.perform(get("/notifications").with(user("1")).session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("notifications"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Transfer sent")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Practice transfer")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Mark all as read")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("New")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(reference))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("does not send email, SMS")));

        verify(historyService).getActivity(eq(current), eq(TransactionFilter.empty()), eq(0), eq(10));
    }

    @Test
    void markAllAsReadUsesOnlyTheSignedInAccountsRecentNotifications() throws Exception {
        Transaction notice = new Transaction(9, current.getId(), TransactionType.TRANSFER_SENT,
            new BigDecimal("12.50"), "Practice transfer", LocalDateTime.of(2026, 9, 1, 12, 0),
            UUID.randomUUID().toString());
        TransactionActivity recent = new TransactionActivity(List.of(notice), TransactionFilter.empty(), 0, 10,
            1, 1, BigDecimal.ZERO.setScale(2), new BigDecimal("12.50"));
        when(historyService.getActivity(eq(current), any(), eq(0), eq(10))).thenReturn(recent);

        mvc.perform(post("/notifications/read").with(user("1")).with(csrf()).session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/notifications"));

        verify(historyService).markRecentNotificationsRead(current, recent.transactions());
    }

    @Test
    void helpStatesSimulatorRecoveryAndSupportLimits() throws Exception {
        mvc.perform(get("/help").with(user("1")).session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("help"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("not connected to a bank")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Forgotten-PIN recovery")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("complaint channels are not configured")));
    }

    @Test
    void helpRequiresAnAuthenticatedAccount() throws Exception {
        mvc.perform(get("/help"))
            .andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/insights", "/notifications", "/help"})
    void accountLookupFailureReturnsAUsableServiceUnavailablePage(String path) throws Exception {
        when(accounts.current(any())).thenThrow(new SQLException("private-account-diagnostic"));
        session.setAttribute("user", current);

        mvc.perform(get(path).with(user("1")).session(session))
            .andExpect(status().isServiceUnavailable())
            .andExpect(view().name("service-unavailable"))
            .andExpect(model().attributeDoesNotExist("user", "insights", "notices"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Request temporarily unavailable")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "or its receipt before trying again")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-account-diagnostic"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getMobileNumber()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getFullName()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getBankAccount().getAccountId()))));

        assertFalse(session.isInvalid());
        verifyNoInteractions(historyService);
    }
}
