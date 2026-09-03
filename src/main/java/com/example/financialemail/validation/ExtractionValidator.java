package com.example.financialemail.validation;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExtractionValidator {
    private final PanValidator panValidator;

    public ExtractionValidator(PanValidator panValidator) {
        this.panValidator = panValidator;
    }

    public EmailAnalysis validateAndNormalize(EmailAnalysis analysis) {
        if (analysis == null || analysis.intent() == null || analysis.entities() == null) {
            throw new InvalidExtractionException(List.of("Model response must include intent and entities"));
        }
        if (analysis.confidence() < 0 || analysis.confidence() > 1) {
            throw new InvalidExtractionException(List.of("confidence must be between 0 and 1"));
        }

        ExtractedEntities entities = analysis.entities();
        String pan = panValidator.normalizeValidPan(entities.pan()).orElse(null);
        if (entities.pan() != null && !entities.pan().isBlank() && pan == null) {
            throw new InvalidExtractionException(List.of("Extracted PAN has an invalid format"));
        }

        TransactionType transactionType = entities.transactionType() == null
                ? TransactionType.UNKNOWN : entities.transactionType();
        TransactionDate transactionDate = normalizeTransactionDate(entities.transactionDate());
        ExtractedEntities normalized = new ExtractedEntities(pan, blankToNull(entities.folioNumber()),
                blankToNull(entities.fundName()), transactionType, transactionDate,
                blankToNull(entities.transactionReference()));
        return new EmailAnalysis(analysis.intent(), analysis.confidence(), normalized,
                blankToNull(analysis.customerSummary()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TransactionDate normalizeTransactionDate(TransactionDate transactionDate) {
        if (transactionDate == null) {
            return new TransactionDate(null, null);
        }

        if (transactionDate.fromDate() == null && transactionDate.toDate() != null) {
            return new TransactionDate(transactionDate.toDate(), transactionDate.toDate());
        }
        if (transactionDate.fromDate() != null && transactionDate.toDate() == null) {
            return new TransactionDate(transactionDate.fromDate(), transactionDate.fromDate());
        }
        if (transactionDate.fromDate() != null
                && transactionDate.fromDate().isAfter(transactionDate.toDate())) {
            throw new InvalidExtractionException(List.of("transactionDate.fromDate must not be after toDate"));
        }
        return transactionDate;
    }
}
