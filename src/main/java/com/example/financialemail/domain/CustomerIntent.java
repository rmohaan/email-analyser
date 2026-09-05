package com.example.financialemail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum CustomerIntent {
    STATEMENT_OF_ACCOUNT,
    CAPITAL_GAINS_STATEMENT,
    TRANSACTION_STATUS,
    PURCHASE_STATUS,
    REDEMPTION_STATUS,
    SIP_STATUS,
    DIVIDEND_STATUS,
    TAX_STATEMENT,
    FOLIO_DETAILS,
    UNKNOWN;

    @JsonCreator
    public static CustomerIntent fromValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
