package com.example.financialemail.workflow;

import java.util.List;

public record ApiResponseEvaluation(
        ApiResponseOutcome outcome,
        List<DownstreamRecord> relevantRecords,
        String reason) {

    public ApiResponseEvaluation {
        relevantRecords = relevantRecords == null ? List.of() : List.copyOf(relevantRecords);
    }
}
