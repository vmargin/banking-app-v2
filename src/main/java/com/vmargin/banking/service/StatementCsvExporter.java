package com.vmargin.banking.service;

import com.vmargin.banking.model.Transaction;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Produces an escaped CSV statement without exposing internal database identifiers. */
public class StatementCsvExporter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String HEADER = "Date,Account,Currency,Type,Category,Direction,Amount,Details,Reference\r\n";

    public String export(List<Transaction> transactions) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (Transaction transaction : transactions) {
            csv.append(cell(DATE_FORMAT.format(transaction.getOccurredAt()), false)).append(',')
                .append(cell(transaction.getAccountName(), true)).append(',')
                .append(cell(transaction.getCurrencyCode(), false)).append(',')
                .append(cell(transaction.getType().name(), false)).append(',')
                .append(cell(transaction.getCategory().displayName(), false)).append(',')
                .append(cell(transaction.getType().isIncoming() ? "IN" : "OUT", false)).append(',')
                .append(cell((transaction.getType().isIncoming() ? "+" : "-")
                    + transaction.getAmount().setScale(2).toPlainString(), false)).append(',')
                .append(cell(transaction.getDetails(), true)).append(',')
                .append(cell(transaction.getReference() == null ? "" : transaction.getReference(), true))
                .append("\r\n");
        }
        return csv.toString();
    }

    private String cell(String value, boolean untrustedText) {
        String safeValue = untrustedText ? neutralizeFormula(value) : value;
        return '"' + safeValue.replace("\"", "\"\"") + '"';
    }

    private String neutralizeFormula(String value) {
        int firstContent = 0;
        while (firstContent < value.length()
            && (Character.isWhitespace(value.charAt(firstContent)) || value.charAt(firstContent) == '\uFEFF')) {
            firstContent++;
        }
        if (firstContent < value.length() && "=+-@".indexOf(value.charAt(firstContent)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
