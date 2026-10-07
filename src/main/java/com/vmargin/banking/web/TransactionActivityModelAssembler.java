package com.vmargin.banking.web;

import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.TransactionHistoryService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Builds the shared activity model used by the dashboard, activity page, and statement export. */
@Component
public final class TransactionActivityModelAssembler {
    private final TransactionHistoryService historyService;
    private final AccountService accountService;

    public TransactionActivityModelAssembler(TransactionHistoryService historyService, AccountService accountService) {
        this.historyService = historyService;
        this.accountService = accountService;
    }

    public TransactionFilter parseFilter(User user, String type, String fromDate, String toDate,
                                         String search, String rawAccountId) throws SQLException {
        TransactionType transactionType = null;
        try {
            if (type != null && !type.isBlank()) {
                transactionType = TransactionType.valueOf(type.trim());
            }
            Long accountId = null;
            if (rawAccountId != null && !rawAccountId.isBlank()) {
                accountId = Long.valueOf(rawAccountId.trim());
                if (accountId < 1 || accountService.get(user, accountId).isEmpty()) {
                    throw new IllegalArgumentException("Account filter is not available.");
                }
            }
            return new TransactionFilter(transactionType, parseDate(fromDate), parseDate(toDate), search, accountId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Transaction account filter is invalid.");
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Transaction filters are invalid.");
        }
    }

    public void populate(User user, Model model, String type, String fromDate, String toDate,
                         String search, String accountId, String page, String pageSize) {
        model.addAttribute("transactionTypes", TransactionType.values());
        model.addAttribute("filterType", valueOrEmpty(type));
        model.addAttribute("filterFromDate", valueOrEmpty(fromDate));
        model.addAttribute("filterToDate", valueOrEmpty(toDate));
        model.addAttribute("filterSearch", valueOrEmpty(search));
        model.addAttribute("filterAccountId", valueOrEmpty(accountId));
        TransactionFilter filter = TransactionFilter.empty();
        try {
            filter = parseFilter(user, type, fromDate, toDate, search, accountId);
            model.addAttribute("selectedAccountId", filter.accountId());
            int requestedPage = parseInteger(page, 0, "Page number");
            int requestedPageSize = parseInteger(pageSize, TransactionHistoryService.DEFAULT_PAGE_SIZE, "Page size");
            TransactionActivity activity = historyService.getActivity(user, filter, requestedPage, requestedPageSize);
            model.addAttribute("activity", activity);
            model.addAttribute("transactions", activity.transactions());
            model.addAttribute("activityAvailable", true);
            model.addAttribute("effectivePageSize", activity.pageSize());
        } catch (IllegalArgumentException exception) {
            model.addAttribute("selectedAccountId", null);
            model.addAttribute("error", "Check the activity filters and page values, then try again.");
            model.addAttribute("activity", emptyActivity(filter));
            model.addAttribute("transactions", List.of());
            model.addAttribute("activityAvailable", false);
            model.addAttribute("effectivePageSize", TransactionHistoryService.DEFAULT_PAGE_SIZE);
        } catch (SQLException exception) {
            model.addAttribute("error", "Transaction activity is temporarily unavailable.");
            model.addAttribute("activity", emptyActivity(filter));
            model.addAttribute("transactions", List.of());
            model.addAttribute("activityAvailable", false);
            model.addAttribute("effectivePageSize", TransactionHistoryService.DEFAULT_PAGE_SIZE);
        }
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private int parseInteger(String value, int defaultValue, String field) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private TransactionActivity emptyActivity(TransactionFilter filter) {
        return new TransactionActivity(List.of(), filter, 0, TransactionHistoryService.DEFAULT_PAGE_SIZE,
            0, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }
}
