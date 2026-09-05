package com.example.financialemail.workflow;

import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.RequestClassification;
import com.example.financialemail.domain.RequestSubtype;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
import com.example.financialemail.routing.DownstreamApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseEvaluatorTest {
    private static final SearchWindow WINDOW = new SearchWindow(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    private final ApiResponseEvaluator evaluator = new ApiResponseEvaluator();

    @Test
    void treatsMultipleProcessedTransactionsAsAnAnswerAndDoesNotRequireExpansion() {
        DownstreamApiResponse response = DownstreamApiResponse.success(DownstreamApi.API_1,
                List.of(record("TX-1", DownstreamRecordStatus.PROCESSED, LocalDate.of(2026, 8, 10)),
                        record("TX-2", DownstreamRecordStatus.PROCESSED, LocalDate.of(2026, 8, 20))), "Two results");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.MATCH_FOUND);
        assertThat(evaluation.relevantRecords()).hasSize(2);
    }

    @Test
    void returnsPartialWhenAnyRelevantRecordHasNoStatus() {
        DownstreamApiResponse response = DownstreamApiResponse.success(DownstreamApi.API_1,
                List.of(record("TX-1", DownstreamRecordStatus.PROCESSED, LocalDate.of(2026, 8, 10)),
                        record("TX-2", null, LocalDate.of(2026, 8, 20))), "Incomplete result");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.PARTIAL_MATCH);
    }

    @Test
    void ignoresRecordsOutsideTheRequestedWindow() {
        DownstreamApiResponse response = DownstreamApiResponse.success(DownstreamApi.API_1,
                List.of(record("TX-1", DownstreamRecordStatus.PROCESSED, LocalDate.of(2026, 7, 31))), "Old result");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.NO_MATCH);
    }

    @Test
    void treatsRateLimitingAsRetryable() {
        DownstreamApiResponse response = DownstreamApiResponse.failure(DownstreamApi.API_1, 429,
                DownstreamFailureType.NONE, "RATE_LIMIT", "Try later");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.RETRYABLE_ERROR);
    }

    @Test
    void rejectsSuccessfulResponsesWithAMissingRecordsField() {
        DownstreamApiResponse response = new DownstreamApiResponse(DownstreamApi.API_1, 200,
                true, null, DownstreamFailureType.NONE, null, "Malformed response");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.FATAL_ERROR);
    }

    @Test
    void rejectsAnInvalidHttpStatusInsteadOfTreatingItAsRetryable() {
        DownstreamApiResponse response = new DownstreamApiResponse(DownstreamApi.API_1, 999,
                true, List.of(), DownstreamFailureType.NONE, null, "Invalid status");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.FATAL_ERROR);
    }

    @Test
    void treatsAnUnknownVendorStatusAsAPartialAnswer() {
        DownstreamApiResponse response = DownstreamApiResponse.success(DownstreamApi.API_1,
                List.of(record("TX-1", DownstreamRecordStatus.UNKNOWN,
                        LocalDate.of(2026, 8, 10))), "Unknown status");

        ApiResponseEvaluation evaluation = evaluator.evaluate(
                response, DownstreamApi.API_1, analysis(null), WINDOW);

        assertThat(evaluation.outcome()).isEqualTo(ApiResponseOutcome.PARTIAL_MATCH);
    }

    private EmailAnalysis analysis(String reference) {
        return new EmailAnalysis(CustomerIntent.REDEMPTION_STATUS, 0.9,
                new ExtractedEntities("ABCDE1234F", "FOLIO-1", "Example Fund",
                        TransactionType.REDEMPTION,
                        new TransactionDate(WINDOW.fromDate(), WINDOW.toDate()), reference),
                RequestClassification.fromSubtype(RequestSubtype.REDEMPTION), "Status request");
    }

    private DownstreamRecord record(String reference, DownstreamRecordStatus status, LocalDate date) {
        return new DownstreamRecord(reference, reference, "ABCDE1234F", "FOLIO-1",
                "Example Fund", RequestSubtype.REDEMPTION, date, status, null,
                Instant.parse("2026-08-31T00:00:00Z"), Map.of());
    }
}
