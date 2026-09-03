package com.example.financialemail.service;

import com.example.financialemail.domain.TransactionDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeTransactionDateResolverTest {
    private final RelativeTransactionDateResolver resolver = new RelativeTransactionDateResolver();

    @Test
    void resolvesLastWeekFromTheEmailReceptionDate() {
        assertThat(resolver.resolve("I purchased 310 units last week.", LocalDate.of(2026, 9, 3)))
                .contains(new TransactionDate(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30)));
    }

    @Test
    void resolvesLastMonthAcrossAYearBoundary() {
        assertThat(resolver.resolve("Please send last month's statement.", LocalDate.of(2026, 1, 8)))
                .contains(new TransactionDate(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void leavesNonRelativeDatesForTheModelExtraction() {
        assertThat(resolver.resolve("I purchased units on 2026-08-15.", LocalDate.of(2026, 9, 3)))
                .isEmpty();
    }
}
