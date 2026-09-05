package com.example.financialemail.validation;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.RequestClassification;
import com.example.financialemail.domain.RequestSubtype;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ExtractionValidator {
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(2100, 12, 31);
    private static final int MAX_ENTITY_LENGTH = 500;
    private static final int MAX_SUMMARY_LENGTH = 2_000;
    private final PanValidator panValidator;

    public ExtractionValidator(PanValidator panValidator) {
        this.panValidator = panValidator;
    }

    public EmailAnalysis validateAndNormalize(EmailAnalysis analysis) {
        if (analysis == null || analysis.intent() == null || analysis.entities() == null) {
            throw new InvalidExtractionException(List.of("Model response must include intent and entities"));
        }
        if (!Double.isFinite(analysis.confidence())
                || analysis.confidence() < 0 || analysis.confidence() > 1) {
            throw new InvalidExtractionException(List.of("confidence must be between 0 and 1"));
        }

        ExtractedEntities entities = analysis.entities();
        String pan = panValidator.normalizeValidPan(entities.pan()).orElse(null);
        if (entities.pan() != null && !entities.pan().isBlank() && pan == null) {
            throw new InvalidExtractionException(List.of("Extracted PAN has an invalid format"));
        }

        TransactionType transactionType = normalizeTransactionType(
                analysis.intent(), entities.transactionType());
        TransactionDate transactionDate = normalizeTransactionDate(entities.transactionDate());
        ExtractedEntities normalized = new ExtractedEntities(pan,
                normalizeText("folioNumber", entities.folioNumber(), MAX_ENTITY_LENGTH),
                normalizeText("fundName", entities.fundName(), MAX_ENTITY_LENGTH),
                transactionType, transactionDate,
                normalizeText("transactionReference", entities.transactionReference(),
                        MAX_ENTITY_LENGTH));
        RequestSubtype subtype = normalizeRequestSubtype(
                analysis.intent(), analysis.requestClassification(), transactionType);
        RequestClassification classification = RequestClassification.fromSubtype(subtype);
        return new EmailAnalysis(analysis.intent(), analysis.confidence(), normalized, classification,
                normalizeText("customerSummary", analysis.customerSummary(), MAX_SUMMARY_LENGTH));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TransactionDate normalizeTransactionDate(TransactionDate transactionDate) {
        if (transactionDate == null) {
            return new TransactionDate(null, null);
        }

        LocalDate fromDate = transactionDate.fromDate();
        LocalDate toDate = transactionDate.toDate();
        if (fromDate == null && toDate != null) {
            fromDate = toDate;
        } else if (fromDate != null && toDate == null) {
            toDate = fromDate;
        }
        if (fromDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidExtractionException(List.of("transactionDate.fromDate must not be after toDate"));
        }
        if (isOutsideSupportedRange(fromDate) || isOutsideSupportedRange(toDate)) {
            throw new InvalidExtractionException(List.of(
                    "transactionDate must be between 1900-01-01 and 2100-12-31"));
        }
        return new TransactionDate(fromDate, toDate);
    }

    private boolean isOutsideSupportedRange(LocalDate date) {
        return date != null && (date.isBefore(MIN_SUPPORTED_DATE) || date.isAfter(MAX_SUPPORTED_DATE));
    }

    private String normalizeText(String field, String value, int maximumLength) {
        String normalized = blankToNull(value);
        if (normalized != null && normalized.length() > maximumLength) {
            throw new InvalidExtractionException(List.of(
                    field + " must not exceed " + maximumLength + " characters"));
        }
        return normalized;
    }

    private TransactionType normalizeTransactionType(CustomerIntent intent,
                                                     TransactionType transactionType) {
        if (intent == CustomerIntent.STATEMENT_OF_ACCOUNT
                || intent == CustomerIntent.CAPITAL_GAINS_STATEMENT
                || intent == CustomerIntent.TAX_STATEMENT) {
            return TransactionType.UNKNOWN;
        }
        return transactionType == null ? TransactionType.UNKNOWN : transactionType;
    }

    private RequestSubtype normalizeRequestSubtype(CustomerIntent intent,
                                                   RequestClassification classification,
                                                   TransactionType transactionType) {
        if (intent == CustomerIntent.STATEMENT_OF_ACCOUNT
                || intent == CustomerIntent.CAPITAL_GAINS_STATEMENT
                || intent == CustomerIntent.TAX_STATEMENT) {
            return RequestSubtype.UNKNOWN;
        }
        RequestSubtype extractedSubtype = classification == null || classification.subtype() == null
                ? RequestSubtype.UNKNOWN : classification.subtype();
        RequestSubtype transactionSubtype = switch (transactionType) {
            case PURCHASE -> RequestSubtype.PURCHASE;
            case REDEMPTION -> RequestSubtype.REDEMPTION;
            case SWITCH -> RequestSubtype.SWITCH;
            case SIP -> RequestSubtype.SIP;
            case DIVIDEND, UNKNOWN -> RequestSubtype.UNKNOWN;
        };

        if (extractedSubtype == RequestSubtype.UNKNOWN) {
            return transactionSubtype;
        }
        if (transactionSubtype != RequestSubtype.UNKNOWN && extractedSubtype != transactionSubtype) {
            throw new InvalidExtractionException(List.of(
                    "requestClassification.subtype conflicts with entities.transactionType"));
        }
        return extractedSubtype;
    }
}
