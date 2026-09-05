package com.example.financialemail.workflow;

import java.time.LocalDate;
import java.util.Objects;

public record SearchWindow(
        LocalDate fromDate,
        LocalDate toDate) {

    public SearchWindow {
        Objects.requireNonNull(fromDate, "fromDate is required");
        Objects.requireNonNull(toDate, "toDate is required");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }
}
