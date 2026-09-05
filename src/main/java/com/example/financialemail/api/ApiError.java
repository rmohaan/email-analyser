package com.example.financialemail.api;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, String message, List<String> details) {
    public ApiError {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        message = message == null || message.isBlank() ? "Request could not be completed" : message;
        details = details == null ? List.of() : details.stream()
                .filter(detail -> detail != null && !detail.isBlank())
                .toList();
    }
}
