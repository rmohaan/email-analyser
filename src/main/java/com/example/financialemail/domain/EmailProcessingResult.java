package com.example.financialemail.domain;

import com.example.financialemail.workflow.WorkflowResult;

public record EmailProcessingResult(
        EmailAnalysis analysis,
        WorkflowResult workflow) {
}
