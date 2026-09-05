package com.example.financialemail.workflow;

import com.example.financialemail.routing.DownstreamApi;

import java.util.List;

public record WorkflowResult(
        String workflowId,
        WorkflowState finalState,
        WorkflowOutcome outcome,
        DownstreamApi selectedApi,
        DownstreamApiResponse downstreamResponse,
        List<WorkflowState> stateHistory,
        List<SearchAttempt> attempts) {

    public WorkflowResult {
        stateHistory = List.copyOf(stateHistory);
        attempts = List.copyOf(attempts);
    }
}
