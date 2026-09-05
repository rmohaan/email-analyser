package com.example.financialemail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum RequestCategory {
    FINANCIAL_TRANSACTION,
    NFT_SIP,
    NFT_STP_SWP_PROSPECT,
    NFT_MODIFICATION,
    UNKNOWN;

    @JsonCreator
    public static RequestCategory fromValue(String value) {
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
