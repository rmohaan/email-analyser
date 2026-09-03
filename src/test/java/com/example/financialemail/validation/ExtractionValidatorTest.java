package com.example.financialemail.validation;

import com.example.financialemail.domain.CustomerIntent;
import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.domain.TransactionType;
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
}
