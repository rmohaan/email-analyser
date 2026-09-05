package com.example.financialemail.workflow;

import com.example.financialemail.domain.RequestSubtype;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * API-neutral record produced by an API-specific response adapter.
 */
public record DownstreamRecord(
        String recordId,
        String transactionReference,
        String pan,
        String folioNumber,
        String fundName,
        RequestSubtype subtype,
        LocalDate eventDate,
        DownstreamRecordStatus status,
        String statusReason,
        Instant lastUpdatedAt,
        Map<String, String> attributes) {

    public DownstreamRecord {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
