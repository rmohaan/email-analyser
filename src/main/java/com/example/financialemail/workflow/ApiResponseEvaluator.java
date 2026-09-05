package com.example.financialemail.workflow;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.RequestSubtype;
import com.example.financialemail.routing.DownstreamApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class ApiResponseEvaluator {

    public ApiResponseEvaluation evaluate(DownstreamApiResponse response, DownstreamApi expectedApi,
                                          EmailAnalysis analysis, SearchWindow window) {
        if (expectedApi == null) {
            return fatal("The requested API was not specified");
        }
        if (response == null) {
            return fatal("The downstream client returned no response");
        }
        if (response.api() != expectedApi) {
            return fatal("The response API does not match the requested API");
        }
        if (response.failureType() == DownstreamFailureType.RETRYABLE) {
            return new ApiResponseEvaluation(ApiResponseOutcome.RETRYABLE_ERROR, List.of(),
                    "The downstream service reported a retryable failure");
        }
        if (response.failureType() == DownstreamFailureType.FATAL) {
            return fatal("The downstream service reported a non-retryable failure");
        }
        if (response.httpStatus() < 100 || response.httpStatus() > 599) {
            return fatal("The response contains an invalid HTTP status");
        }
        if (isRetryableHttpStatus(response.httpStatus())) {
            return new ApiResponseEvaluation(ApiResponseOutcome.RETRYABLE_ERROR, List.of(),
                    "The downstream service reported a retryable HTTP status");
        }
        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            return fatal("The downstream service reported a non-retryable HTTP status");
        }
        if (response.httpStatus() == 204) {
            return noMatch("The downstream service returned no content");
        }
        if (!response.responseBodyPresent()) {
            return fatal("A successful downstream response did not contain a response body");
        }
        if (response.records() == null) {
            return fatal("The downstream response did not contain the required records field");
        }
        if (response.records().stream().anyMatch(Objects::isNull)) {
            return fatal("The downstream response contains a null record");
        }
        if (analysis == null || analysis.entities() == null || window == null) {
            return fatal("The response could not be evaluated without a valid request context");
        }

        List<DownstreamRecord> relevant = response.records().stream()
                .filter(record -> isRelevant(record, analysis, window))
                .toList();
        if (relevant.isEmpty()) {
            return noMatch("No records matched the request identity, type, reference and date filters");
        }

        List<DownstreamRecord> incomplete = relevant.stream()
                .filter(record -> !containsCompleteAnswer(record, analysis))
                .toList();
        if (!incomplete.isEmpty()) {
            return new ApiResponseEvaluation(ApiResponseOutcome.PARTIAL_MATCH, relevant,
                    incomplete.size() + " relevant record(s) lacked a canonical status or required correlation field");
        }
        return new ApiResponseEvaluation(ApiResponseOutcome.MATCH_FOUND, relevant,
                relevant.size() + " relevant record(s) contained a usable status");
    }

    private boolean containsCompleteAnswer(DownstreamRecord record, EmailAnalysis analysis) {
        if (record.status() == null || record.status() == DownstreamRecordStatus.UNKNOWN) {
            return false;
        }
        ExtractedEntities expected = analysis.entities();
        if (!isBlank(expected.transactionReference()) && isBlank(record.transactionReference())) {
            return false;
        }
        RequestSubtype expectedSubtype = analysis.requestClassification() == null
                ? RequestSubtype.UNKNOWN : analysis.requestClassification().subtype();
        return expectedSubtype == null || expectedSubtype == RequestSubtype.UNKNOWN
                || (record.subtype() != null && record.subtype() != RequestSubtype.UNKNOWN);
    }

    private boolean isRelevant(DownstreamRecord record, EmailAnalysis analysis, SearchWindow window) {
        ExtractedEntities expected = analysis.entities();
        RequestSubtype expectedSubtype = analysis.requestClassification() == null
                ? RequestSubtype.UNKNOWN : analysis.requestClassification().subtype();

        if (differentWhenBothPresent(expected.transactionReference(), record.transactionReference())) {
            return false;
        }
        if (differentWhenBothPresent(expected.pan(), record.pan())) {
            return false;
        }
        if (differentWhenBothPresent(expected.folioNumber(), record.folioNumber())) {
            return false;
        }
        if (expectedSubtype != null && expectedSubtype != RequestSubtype.UNKNOWN
                && record.subtype() != null && record.subtype() != RequestSubtype.UNKNOWN
                && expectedSubtype != record.subtype()) {
            return false;
        }
        return record.eventDate() == null
                || (!record.eventDate().isBefore(window.fromDate())
                && !record.eventDate().isAfter(window.toDate()));
    }

    private boolean differentWhenBothPresent(String expected, String actual) {
        if (isBlank(expected) || isBlank(actual)) {
            return false;
        }
        return !normalize(expected).equals(normalize(actual));
    }

    private String normalize(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isRetryableHttpStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private ApiResponseEvaluation fatal(String reason) {
        return new ApiResponseEvaluation(ApiResponseOutcome.FATAL_ERROR, List.of(), reason);
    }

    private ApiResponseEvaluation noMatch(String reason) {
        return new ApiResponseEvaluation(ApiResponseOutcome.NO_MATCH, List.of(), reason);
    }
}
