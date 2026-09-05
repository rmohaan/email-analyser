package com.example.financialemail.workflow;

import com.example.financialemail.routing.DownstreamApi;

public record SearchAttempt(
        DownstreamApi api,
        SearchPhase phase,
        SearchWindow window,
        ApiResponseOutcome outcome,
        int relevantRecordCount,
        String reason) {
}
