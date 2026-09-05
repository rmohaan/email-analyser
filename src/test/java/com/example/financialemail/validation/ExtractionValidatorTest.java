package com.example.financialemail.validation;

import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
import com.example.financialemail.domain.RequestCategory;
import com.example.financialemail.domain.RequestClassification;
import com.example.financialemail.domain.RequestSubtype;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionValidatorTest {
    private final ExtractionValidator validator = new ExtractionValidator(new PanValidator());

    @Test
    void normalizesExtractedPan() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.REDEMPTION_STATUS, 0.91,
                new ExtractedEntities("abcde1234f", " 12345678 ", null, TransactionType.REDEMPTION,
                        null, null), " Redemption confirmation is requested. ");

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.entities().pan()).isEqualTo("ABCDE1234F");
        assertThat(result.entities().folioNumber()).isEqualTo("12345678");
    }

    @Test
    void rejectsInvalidExtractedPan() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.UNKNOWN, 0.5,
                new ExtractedEntities("invalid", null, null, TransactionType.UNKNOWN, null, null), null);

        assertThatThrownBy(() -> validator.validateAndNormalize(analysis))
                .isInstanceOf(InvalidExtractionException.class);
    }

    @Test
    void keepsTransactionDateAsAnObjectWhenNoDateWasExtracted() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.UNKNOWN, 0.5,
                new ExtractedEntities(null, null, null, TransactionType.UNKNOWN, null, null), null);

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.entities().transactionDate()).isEqualTo(new TransactionDate(null, null));
    }

    @Test
    void fillsBothBoundsWhenOnlyOneExactDateWasExtracted() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.TRANSACTION_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.PURCHASE,
                        new TransactionDate(date, null), null), null);

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.entities().transactionDate()).isEqualTo(new TransactionDate(date, date));
    }

    @Test
    void rejectsAnInvertedTransactionDateRange() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.TRANSACTION_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.PURCHASE,
                        new TransactionDate(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)), null), null);

        assertThatThrownBy(() -> validator.validateAndNormalize(analysis))
                .isInstanceOf(InvalidExtractionException.class)
                .satisfies(exception -> assertThat(((InvalidExtractionException) exception).details())
                        .containsExactly("transactionDate.fromDate must not be after toDate"));
    }

    @Test
    void rejectsExtremeDatesBeforeWorkflowDateArithmetic() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.TRANSACTION_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.PURCHASE,
                        new TransactionDate(LocalDate.of(2200, 1, 1), null), null), null);

        assertThatThrownBy(() -> validator.validateAndNormalize(analysis))
                .isInstanceOf(InvalidExtractionException.class)
                .satisfies(exception -> assertThat(((InvalidExtractionException) exception).details())
                        .containsExactly("transactionDate must be between 1900-01-01 and 2100-12-31"));
    }

    @Test
    void rejectsNonFiniteConfidence() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.UNKNOWN, Double.NaN,
                new ExtractedEntities(null, null, null, TransactionType.UNKNOWN, null, null), null);

        assertThatThrownBy(() -> validator.validateAndNormalize(analysis))
                .isInstanceOf(InvalidExtractionException.class);
    }

    @Test
    void derivesTheCanonicalRoutingCategoryFromTheSubtype() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.SIP_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.SIP, null, null),
                new RequestClassification(RequestCategory.FINANCIAL_TRANSACTION, RequestSubtype.SIP),
                "SIP status requested");

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.requestClassification()).isEqualTo(
                new RequestClassification(RequestCategory.NFT_SIP, RequestSubtype.SIP));
    }

    @Test
    void derivesAMissingRoutingSubtypeFromTheTransactionType() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.REDEMPTION_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.REDEMPTION, null, null),
                new RequestClassification(RequestCategory.UNKNOWN, RequestSubtype.UNKNOWN),
                "Redemption status requested");

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.requestClassification()).isEqualTo(
                new RequestClassification(RequestCategory.FINANCIAL_TRANSACTION, RequestSubtype.REDEMPTION));
    }

    @Test
    void rejectsConflictingTransactionAndRoutingClassifications() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.REDEMPTION_STATUS, 0.9,
                new ExtractedEntities(null, null, null, TransactionType.REDEMPTION, null, null),
                new RequestClassification(RequestCategory.FINANCIAL_TRANSACTION, RequestSubtype.PURCHASE),
                "Redemption status requested");

        assertThatThrownBy(() -> validator.validateAndNormalize(analysis))
                .isInstanceOf(InvalidExtractionException.class)
                .satisfies(exception -> assertThat(((InvalidExtractionException) exception).details())
                        .containsExactly("requestClassification.subtype conflicts with entities.transactionType"));
    }

    @Test
    void documentRequestsCannotBeRoutedAsFinancialTransactions() {
        EmailAnalysis analysis = new EmailAnalysis(CustomerIntent.STATEMENT_OF_ACCOUNT, 0.9,
                new ExtractedEntities(null, "12345678", null, TransactionType.REDEMPTION, null, null),
                new RequestClassification(RequestCategory.FINANCIAL_TRANSACTION, RequestSubtype.REDEMPTION),
                "Statement requested");

        EmailAnalysis result = validator.validateAndNormalize(analysis);

        assertThat(result.entities().transactionType()).isEqualTo(TransactionType.UNKNOWN);
        assertThat(result.requestClassification()).isEqualTo(
                new RequestClassification(RequestCategory.UNKNOWN, RequestSubtype.UNKNOWN));
    }
}
