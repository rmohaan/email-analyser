package com.example.financialemail.workflow;

import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.RequestClassification;
import com.example.financialemail.domain.RequestSubtype;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
import com.example.financialemail.routing.DownstreamApi;
import com.example.financialemail.routing.RequestRouter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostClassificationWorkflowTest {
    private static final LocalDate RECEPTION_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate PROCESSING_DATE = LocalDate.of(2026, 7, 1);

    @Test
    void stopsAfterTheInitialApiReturnsAMatch() {
        ScriptedClient client = new ScriptedClient(ApiResponseOutcome.MATCH_FOUND);

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.REDEMPTION_STATUS, RequestSubtype.REDEMPTION),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(result.selectedApi()).isEqualTo(DownstreamApi.API_1);
        assertThat(result.attempts()).hasSize(1);
    }

    @Test
    void stopsAfterOneCallWhenEveryReturnedTransactionIsProcessed() {
        DownstreamApiClient client = (api, analysis, window, phase, workflowId) ->
                DownstreamApiResponse.success(api, List.of(
                        processedRecord("TX-1", analysis, window),
                        processedRecord("TX-2", analysis, window)), "Two processed transactions");

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.REDEMPTION_STATUS, RequestSubtype.REDEMPTION),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(result.attempts()).hasSize(1);
        assertThat(result.attempts().getFirst().relevantRecordCount()).isEqualTo(2);
    }

    @Test
    void expandsDatesButDoesNotSearchOtherApisForASpecificRequest() {
        ScriptedClient client = new ScriptedClient(
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH);

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.REDEMPTION_STATUS, RequestSubtype.REDEMPTION),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.NOT_FOUND);
        assertThat(result.attempts()).extracting(SearchAttempt::api)
                .containsExactly(DownstreamApi.API_1, DownstreamApi.API_1, DownstreamApi.API_1);
        assertThat(result.attempts()).extracting(SearchAttempt::phase)
                .containsExactly(SearchPhase.INITIAL, SearchPhase.BACKWARD_30_DAYS,
                        SearchPhase.FORWARD_30_DAYS);
    }

    @Test
    void searchesAcrossApiFamiliesOnlyAfterAnAmbiguousInitialSearchFindsNothing() {
        ScriptedClient client = new ScriptedClient(
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.NO_MATCH,
                ApiResponseOutcome.MATCH_FOUND);

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.TRANSACTION_STATUS, RequestSubtype.UNKNOWN),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(result.selectedApi()).isEqualTo(DownstreamApi.API_4);
        assertThat(result.attempts()).extracting(SearchAttempt::api)
                .containsExactly(DownstreamApi.API_1, DownstreamApi.API_1, DownstreamApi.API_1,
                        DownstreamApi.API_2, DownstreamApi.API_3, DownstreamApi.API_4);
    }

    @Test
    void doesNotExpandAfterATechnicalFailure() {
        ScriptedClient client = new ScriptedClient(ApiResponseOutcome.RETRYABLE_ERROR);

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.TRANSACTION_STATUS, RequestSubtype.UNKNOWN),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.TECHNICAL_FAILURE);
        assertThat(result.attempts()).hasSize(1);
    }

    @Test
    void convertsAClientTimeoutIntoATechnicalFailure() {
        DownstreamApiClient client = (api, analysis, window, phase, workflowId) -> {
            throw new DownstreamApiCallException("Timed out", true, null);
        };

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.REDEMPTION_STATUS, RequestSubtype.REDEMPTION),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.TECHNICAL_FAILURE);
        assertThat(result.attempts()).hasSize(1);
        assertThat(result.attempts().getFirst().outcome())
                .isEqualTo(ApiResponseOutcome.RETRYABLE_ERROR);
    }

    @Test
    void containsAnUnexpectedClientExceptionAsATechnicalFailure() {
        DownstreamApiClient client = (api, analysis, window, phase, workflowId) -> {
            throw new IllegalStateException("Unexpected adapter bug");
        };

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.REDEMPTION_STATUS, RequestSubtype.REDEMPTION),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.TECHNICAL_FAILURE);
        assertThat(result.attempts()).hasSize(1);
        assertThat(result.downstreamResponse().errorCode())
                .isEqualTo("UNEXPECTED_CLIENT_FAILURE");
    }

    @Test
    void containsIncompleteWorkflowInputAsATechnicalFailure() {
        WorkflowResult result = workflow(new ScriptedClient()).execute(
                null, RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.TECHNICAL_FAILURE);
        assertThat(result.attempts()).isEmpty();
    }

    @Test
    void doesNotCallAnyApiForAnUnrelatedUnknownRequest() {
        ScriptedClient client = new ScriptedClient();

        WorkflowResult result = workflow(client).execute(
                analysis(CustomerIntent.UNKNOWN, RequestSubtype.UNKNOWN),
                RECEPTION_DATE, PROCESSING_DATE);

        assertThat(result.finalState()).isEqualTo(WorkflowState.UNROUTABLE);
        assertThat(result.attempts()).isEmpty();
    }

    private PostClassificationWorkflow workflow(DownstreamApiClient client) {
        return new PostClassificationWorkflow(new RequestRouter(), client,
                new ApiResponseEvaluator(), new DateWindowPlanner());
    }

    private EmailAnalysis analysis(CustomerIntent intent, RequestSubtype subtype) {
        TransactionType transactionType = switch (subtype) {
            case REDEMPTION -> TransactionType.REDEMPTION;
            case PURCHASE -> TransactionType.PURCHASE;
            case SWITCH -> TransactionType.SWITCH;
            case SIP -> TransactionType.SIP;
            default -> TransactionType.UNKNOWN;
        };
        return new EmailAnalysis(intent, 0.9,
                new ExtractedEntities(null, null, null, transactionType,
                        new TransactionDate(LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15)), null),
                RequestClassification.fromSubtype(subtype), "Synthetic request");
    }

    private static DownstreamRecord processedRecord(String id, EmailAnalysis analysis,
                                                    SearchWindow window) {
        var entities = analysis.entities();
        return new DownstreamRecord(id, id, entities.pan(), entities.folioNumber(),
                entities.fundName(), analysis.requestClassification().subtype(), window.toDate(),
                DownstreamRecordStatus.PROCESSED, null,
                Instant.parse("2026-06-01T00:00:00Z"), java.util.Map.of());
    }

    private static final class ScriptedClient implements DownstreamApiClient {
        private final Deque<ApiResponseOutcome> outcomes;
        private final List<DownstreamApi> calls = new ArrayList<>();

        private ScriptedClient(ApiResponseOutcome... outcomes) {
            this.outcomes = new ArrayDeque<>(Arrays.asList(outcomes));
        }

        @Override
        public DownstreamApiResponse call(DownstreamApi api, EmailAnalysis analysis,
                                          SearchWindow window, SearchPhase phase,
                                          String workflowId) {
            calls.add(api);
            ApiResponseOutcome outcome = outcomes.removeFirst();
            return switch (outcome) {
                case MATCH_FOUND -> DownstreamApiResponse.success(api,
                        List.of(record(analysis, window, DownstreamRecordStatus.PROCESSED)),
                        "Scripted match");
                case PARTIAL_MATCH -> DownstreamApiResponse.success(api,
                        List.of(record(analysis, window, null)), "Scripted partial match");
                case NO_MATCH -> DownstreamApiResponse.success(api, List.of(), "Scripted no match");
                case RETRYABLE_ERROR -> DownstreamApiResponse.failure(api, 503,
                        DownstreamFailureType.RETRYABLE, "UNAVAILABLE", "Scripted retryable failure");
                case FATAL_ERROR -> DownstreamApiResponse.failure(api, 400,
                        DownstreamFailureType.FATAL, "BAD_REQUEST", "Scripted fatal failure");
            };
        }

        private DownstreamRecord record(EmailAnalysis analysis, SearchWindow window,
                                        DownstreamRecordStatus status) {
            var entities = analysis.entities();
            return new DownstreamRecord("ID-1", entities.transactionReference(), entities.pan(),
                    entities.folioNumber(), entities.fundName(),
                    analysis.requestClassification().subtype(), window.toDate(), status, null,
                    Instant.parse("2026-06-01T00:00:00Z"), java.util.Map.of());
        }
    }
}
