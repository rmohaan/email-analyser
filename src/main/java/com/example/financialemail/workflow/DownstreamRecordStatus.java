package com.example.financialemail.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** Canonical status vocabulary populated by API-specific adapters. */
public enum DownstreamRecordStatus {
    PROCESSED,
    COMPLETED,
    PENDING,
    IN_PROGRESS,
    FAILED,
    REJECTED,
    CANCELLED,
    APPROVED,
    ACTIVE,
    UNKNOWN;

    @JsonCreator
    public static DownstreamRecordStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
