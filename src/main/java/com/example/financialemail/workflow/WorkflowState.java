package com.example.financialemail.workflow;

public enum WorkflowState {
    CLASSIFIED,
    INITIAL_CALL,
    DATE_EXPANSION,
    BREADTH_SEARCH,
    COMPLETED,
    PARTIAL_RESULT,
    NOT_FOUND,
    TECHNICAL_FAILURE,
    UNROUTABLE
}
