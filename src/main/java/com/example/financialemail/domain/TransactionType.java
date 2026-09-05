package com.example.financialemail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum TransactionType {
    PURCHASE,
    REDEMPTION,
    SIP,
    DIVIDEND,
    SWITCH,
    UNKNOWN;

    @JsonCreator
    public static TransactionType fromValue(String value) {
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
