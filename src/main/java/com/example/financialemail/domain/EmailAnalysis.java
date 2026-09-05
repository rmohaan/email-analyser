package com.example.financialemail.domain;

public record EmailAnalysis(
        CustomerIntent intent,
        double confidence,
        ExtractedEntities entities,
        RequestClassification requestClassification,
        String customerSummary) {

    public EmailAnalysis(CustomerIntent intent, double confidence, ExtractedEntities entities,
                         String customerSummary) {
        this(intent, confidence, entities, RequestClassification.fromSubtype(RequestSubtype.UNKNOWN),
                customerSummary);
    }
}
