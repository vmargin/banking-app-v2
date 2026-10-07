package com.vmargin.banking.model;

public enum CardReplacementReason {
    LOST("Lost"),
    STOLEN("Stolen"),
    DAMAGED("Damaged"),
    EXPIRED("Expired");

    private final String label;

    CardReplacementReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CardReplacementReason parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Choose a replacement reason.");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose a valid replacement reason.");
        }
    }
}
