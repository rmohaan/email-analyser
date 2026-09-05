package com.example.financialemail.workflow;

import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.RequestSubtype;
import com.example.financialemail.routing.DownstreamApi;
import com.example.financialemail.routing.RequestRouter;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostClassificationWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostClassificationWorkflow.class);
    private static final int MAX_CALLS = 6;

    private final RequestRouter requestRouter;
    private final DownstreamApiClient downstreamApiClient;
    private final ApiResponseEvaluator responseEvaluator;
    private final DateWindowPlanner dateWindowPlanner;

    public PostClassificationWorkflow(RequestRouter requestRouter,
                                      DownstreamApiClient downstreamApiClient,
                                      ApiResponseEvaluator responseEvaluator,
                                      DateWindowPlanner dateWindowPlanner) {
        this.requestRouter = requestRouter;
        this.downstreamApiClient = downstreamApiClient;
        this.responseEvaluator = responseEvaluator;
        this.dateWindowPlanner = dateWindowPlanner;
    }

    public WorkflowResult execute(EmailAnalysis analysis, LocalDate receptionDate,
                                  LocalDate processingDate) {
        String workflowId = UUID.randomUUID().toString();
        List<WorkflowState> states = new ArrayList<>();
        List<SearchAttempt> attempts = new ArrayList<>();
        states.add(WorkflowState.CLASSIFIED);

        try {
            return executeSafely(analysis, receptionDate, processingDate, workflowId, states, attempts);
        } catch (RuntimeException exception) {
            LOGGER.error("Workflow {} failed unexpectedly", workflowId, exception);
            if (states.getLast() != WorkflowState.TECHNICAL_FAILURE) {
                states.add(WorkflowState.TECHNICAL_FAILURE);
            }
            return result(workflowId, WorkflowState.TECHNICAL_FAILURE,
                    WorkflowOutcome.TECHNICAL_FAILURE, null, null, states, attempts);
        }
    }

    private WorkflowResult executeSafely(EmailAnalysis analysis, LocalDate receptionDate,
                                         LocalDate processingDate, String workflowId,
                                         List<WorkflowState> states,
                                         List<SearchAttempt> attempts) {
        if (analysis == null || analysis.entities() == null || analysis.intent() == null
                || analysis.requestClassification() == null
                || receptionDate == null || processingDate == null) {
            throw new IllegalArgumentException("Workflow input is incomplete");
        }

        boolean ambiguous = isAmbiguousTransactionRequest(analysis);
        Optional<DownstreamApi> routedApi = requestRouter.route(analysis.requestClassification());
        DownstreamApi primaryApi = routedApi.orElse(ambiguous ? DownstreamApi.API_1 : null);
        if (primaryApi == null) {
            states.add(WorkflowState.UNROUTABLE);
            return result(workflowId, WorkflowState.UNROUTABLE, WorkflowOutcome.UNROUTABLE,
                    null, null, states, attempts);
        }

        SearchWindow initialWindow = dateWindowPlanner.initial(
                analysis.entities().transactionDate(), receptionDate);
        states.add(WorkflowState.INITIAL_CALL);
        AttemptResult initial = call(primaryApi, analysis, initialWindow, SearchPhase.INITIAL,
                workflowId, attempts);
        WorkflowResult terminal = terminalResult(initial, workflowId, states, attempts);
        if (terminal != null) {
            return terminal;
        }

        states.add(WorkflowState.DATE_EXPANSION);
        for (DateWindowPlanner.PlannedWindow planned :
                dateWindowPlanner.expanded(initialWindow, processingDate)) {
            AttemptResult expanded = call(primaryApi, analysis, planned.window(), planned.phase(),
                    workflowId, attempts);
            terminal = terminalResult(expanded, workflowId, states, attempts);
            if (terminal != null) {
                return terminal;
            }
        }

        if (!ambiguous) {
            states.add(WorkflowState.NOT_FOUND);
            return result(workflowId, WorkflowState.NOT_FOUND, WorkflowOutcome.NOT_FOUND,
                    primaryApi, null, states, attempts);
        }

        states.add(WorkflowState.BREADTH_SEARCH);
        SearchWindow breadthWindow = dateWindowPlanner.breadth(initialWindow, processingDate);
        boolean breadthHadTechnicalFailure = false;
        for (DownstreamApi api : breadthApisExcluding(primaryApi)) {
            if (attempts.size() >= MAX_CALLS) {
                break;
            }
            AttemptResult breadth = call(api, analysis, breadthWindow, SearchPhase.BREADTH,
                    workflowId, attempts);
            if (breadth.outcome() == ApiResponseOutcome.MATCH_FOUND
                    || breadth.outcome() == ApiResponseOutcome.PARTIAL_MATCH) {
                return terminalResult(breadth, workflowId, states, attempts);
            }
            if (isTechnicalFailure(breadth.outcome())) {
                breadthHadTechnicalFailure = true;
            }
        }

        if (breadthHadTechnicalFailure) {
            states.add(WorkflowState.TECHNICAL_FAILURE);
            return result(workflowId, WorkflowState.TECHNICAL_FAILURE,
                    WorkflowOutcome.TECHNICAL_FAILURE, null, null, states, attempts);
        }
        states.add(WorkflowState.NOT_FOUND);
        return result(workflowId, WorkflowState.NOT_FOUND, WorkflowOutcome.NOT_FOUND,
                null, null, states, attempts);
    }

    private AttemptResult call(DownstreamApi api, EmailAnalysis analysis, SearchWindow window,
                               SearchPhase phase, String workflowId, List<SearchAttempt> attempts) {
        DownstreamApiResponse response;
        try {
            response = downstreamApiClient.call(api, analysis, window, phase, workflowId);
        } catch (DownstreamApiCallException exception) {
            LOGGER.warn("Downstream call failed: workflowId={}, api={}, retryable={}",
                    workflowId, api, exception.retryable(), exception);
            response = DownstreamApiResponse.failure(api, 0,
                    exception.retryable() ? DownstreamFailureType.RETRYABLE
                            : DownstreamFailureType.FATAL,
                    "CLIENT_CALL_FAILED", "The downstream client call failed");
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected downstream client failure: workflowId={}, api={}",
                    workflowId, api, exception);
            response = DownstreamApiResponse.failure(api, 0, DownstreamFailureType.FATAL,
                    "UNEXPECTED_CLIENT_FAILURE", "The downstream client failed unexpectedly");
        }
        ApiResponseEvaluation evaluation;
        try {
            evaluation = responseEvaluator.evaluate(response, api, analysis, window);
        } catch (RuntimeException exception) {
            LOGGER.error("Response evaluation failed: workflowId={}, api={}", workflowId, api,
                    exception);
            evaluation = new ApiResponseEvaluation(ApiResponseOutcome.FATAL_ERROR, List.of(),
                    "The normalized downstream response could not be evaluated");
        }
        attempts.add(new SearchAttempt(api, phase, window, evaluation.outcome(),
                evaluation.relevantRecords().size(), evaluation.reason()));
        return new AttemptResult(api, response, evaluation.outcome());
    }

    private WorkflowResult terminalResult(AttemptResult attempt, String workflowId,
                                          List<WorkflowState> states,
                                          List<SearchAttempt> attempts) {
        return switch (attempt.outcome()) {
            case MATCH_FOUND -> {
                states.add(WorkflowState.COMPLETED);
                yield result(workflowId, WorkflowState.COMPLETED, WorkflowOutcome.MATCH_FOUND,
                        attempt.api(), attempt.response(), states, attempts);
            }
            case PARTIAL_MATCH -> {
                states.add(WorkflowState.PARTIAL_RESULT);
                yield result(workflowId, WorkflowState.PARTIAL_RESULT, WorkflowOutcome.PARTIAL_RESULT,
                        attempt.api(), attempt.response(), states, attempts);
            }
            case RETRYABLE_ERROR, FATAL_ERROR -> {
                states.add(WorkflowState.TECHNICAL_FAILURE);
                yield result(workflowId, WorkflowState.TECHNICAL_FAILURE,
                        WorkflowOutcome.TECHNICAL_FAILURE, attempt.api(), attempt.response(),
                        states, attempts);
            }
            case NO_MATCH -> null;
        };
    }

    private boolean isAmbiguousTransactionRequest(EmailAnalysis analysis) {
        return analysis.intent() == CustomerIntent.TRANSACTION_STATUS
                && analysis.requestClassification().subtype() == RequestSubtype.UNKNOWN;
    }

    private List<DownstreamApi> breadthApisExcluding(DownstreamApi primaryApi) {
        return Arrays.stream(DownstreamApi.values())
                .filter(api -> api != primaryApi)
                .toList();
    }

    private boolean isTechnicalFailure(ApiResponseOutcome outcome) {
        return outcome == ApiResponseOutcome.RETRYABLE_ERROR
                || outcome == ApiResponseOutcome.FATAL_ERROR;
    }

    private WorkflowResult result(String workflowId, WorkflowState state, WorkflowOutcome outcome,
                                  DownstreamApi selectedApi, DownstreamApiResponse response,
                                  List<WorkflowState> states, List<SearchAttempt> attempts) {
        return new WorkflowResult(workflowId, state, outcome, selectedApi, response, states, attempts);
    }

    private record AttemptResult(
            DownstreamApi api,
            DownstreamApiResponse response,
            ApiResponseOutcome outcome) {
    }
}
