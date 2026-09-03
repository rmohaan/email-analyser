package com.example.financialemail.domain;

import java.time.LocalDate;

public record TransactionDate(
        LocalDate fromDate,
        LocalDate toDate) {
}
