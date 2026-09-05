package com.example.financialemail.workflow;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.routing.DownstreamApi;

public interface DownstreamApiClient {
    /**
     * Calls one API and returns its API-specific payload normalized into the common adapter model.
     * Implementations must not decide MATCH/NO_MATCH; that decision belongs to ApiResponseEvaluator.
     */
    DownstreamApiResponse call(DownstreamApi api, EmailAnalysis analysis, SearchWindow window,
                               SearchPhase phase, String workflowId);
}
