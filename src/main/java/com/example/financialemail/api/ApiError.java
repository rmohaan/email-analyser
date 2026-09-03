package com.example.financialemail.api;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, String message, List<String> details) {
}
