package com.vmargin.banking.model;

/** Fixed local-only biller choices. None of these labels represent a live provider integration. */
public enum DemoBiller {
    ELECTRICITY("Demo electricity", "Electricity"),
    WATER("Demo water", "Water"),
    INTERNET("Demo internet", "Internet"),
    MOBILE("Demo mobile", "Mobile"),
    SCHOOL("Demo school fees", "School fees");

    private final String displayLabel;
    private final String categoryLabel;

    DemoBiller(String displayLabel, String categoryLabel) {
        this.displayLabel = displayLabel;
        this.categoryLabel = categoryLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public String categoryLabel() {
        return categoryLabel;
    }
}
