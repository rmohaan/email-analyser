package com.example.financialemail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum RequestSubtype {
    PURCHASE,
    REDEMPTION,
    SWITCH,
    SIP,
    STP,
    SWP,
    PROSPECT,
    NOMINEE_MODIFICATION,
    ADDRESS_MODIFICATION,
    BANK_MODIFICATION,
    OTHER_MODIFICATION,
    UNKNOWN;

    @JsonCreator
    public static RequestSubtype fromValue(String value) {
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
