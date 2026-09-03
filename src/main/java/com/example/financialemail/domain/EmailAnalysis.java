package com.example.financialemail.domain;

public record EmailAnalysis(
        CustomerIntent intent,
        double confidence,
        ExtractedEntities entities,
        String customerSummary) {
}
