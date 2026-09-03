package com.example.financialemail.domain;

public record ExtractedEntities(
        String pan,
        String folioNumber,
        String fundName,
        TransactionType transactionType,
        TransactionDate transactionDate,
        String transactionReference) {
}
