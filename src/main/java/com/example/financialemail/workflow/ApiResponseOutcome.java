package com.example.financialemail.workflow;

public enum ApiResponseOutcome {
    MATCH_FOUND,
    NO_MATCH,
    PARTIAL_MATCH,
    RETRYABLE_ERROR,
    FATAL_ERROR
}
