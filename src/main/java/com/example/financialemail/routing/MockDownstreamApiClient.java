package com.example.financialemail.routing;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.workflow.ApiResponseOutcome;
import com.example.financialemail.workflow.DownstreamFailureType;
import com.example.financialemail.workflow.DownstreamApiClient;
import com.example.financialemail.workflow.DownstreamApiResponse;
import com.example.financialemail.workflow.DownstreamRecord;
import com.example.financialemail.workflow.DownstreamRecordStatus;
import com.example.financialemail.workflow.SearchPhase;
import com.example.financialemail.workflow.SearchWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

@Component
public class MockDownstreamApiClient implements DownstreamApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockDownstreamApiClient.class);
    private final Map<DownstreamApi, ApiResponseOutcome> configuredOutcomes;

    public MockDownstreamApiClient(
            @Value("${application.mock-downstream.api-1-outcome:MATCH_FOUND}") ApiResponseOutcome api1Outcome,
            @Value("${application.mock-downstream.api-2-outcome:MATCH_FOUND}") ApiResponseOutcome api2Outcome,
            @Value("${application.mock-downstream.api-3-outcome:MATCH_FOUND}") ApiResponseOutcome api3Outcome,
            @Value("${application.mock-downstream.api-4-outcome:MATCH_FOUND}") ApiResponseOutcome api4Outcome) {
        configuredOutcomes = new EnumMap<>(DownstreamApi.class);
        configuredOutcomes.put(DownstreamApi.API_1, api1Outcome);
        configuredOutcomes.put(DownstreamApi.API_2, api2Outcome);
        configuredOutcomes.put(DownstreamApi.API_3, api3Outcome);
        configuredOutcomes.put(DownstreamApi.API_4, api4Outcome);
    }

    @Override
    public DownstreamApiResponse call(DownstreamApi api, EmailAnalysis analysis, SearchWindow window,
                                      SearchPhase phase, String workflowId) {
        LOGGER.info("{} called: workflowId={}, phase={}, fromDate={}, toDate={}",
                api.displayName(), workflowId, phase, window.fromDate(), window.toDate());
        ApiResponseOutcome outcome = configuredOutcomes.get(api);
        return switch (outcome) {
            case MATCH_FOUND -> DownstreamApiResponse.success(api,
                    List.of(matchingRecord(analysis, window, DownstreamRecordStatus.PROCESSED)),
                    "Mock response containing a completed record");
            case PARTIAL_MATCH -> DownstreamApiResponse.success(api,
                    List.of(matchingRecord(analysis, window, null)),
                    "Mock response containing a record without status");
            case NO_MATCH -> DownstreamApiResponse.success(api, List.of(),
                    "Mock response containing no records");
            case RETRYABLE_ERROR -> DownstreamApiResponse.failure(api, 503,
                    DownstreamFailureType.RETRYABLE, "MOCK_UNAVAILABLE",
                    "Mock downstream service unavailable");
            case FATAL_ERROR -> DownstreamApiResponse.failure(api, 400,
                    DownstreamFailureType.FATAL, "MOCK_BAD_REQUEST",
                    "Mock downstream request rejected");
        };
    }

    private DownstreamRecord matchingRecord(EmailAnalysis analysis, SearchWindow window,
                                            DownstreamRecordStatus status) {
        var entities = analysis.entities();
        var subtype = analysis.requestClassification().subtype();
        String reference = entities.transactionReference() == null
                ? "MOCK-" + subtype.name() + "-001" : entities.transactionReference();
        return new DownstreamRecord(reference, reference, entities.pan(), entities.folioNumber(),
                entities.fundName(), subtype, window.toDate(), status, null, Instant.now(), Map.of());
    }
}
