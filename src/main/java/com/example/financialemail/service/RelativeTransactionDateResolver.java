package com.example.financialemail.service;

import com.example.financialemail.domain.TransactionDate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class RelativeTransactionDateResolver {
    private static final Pattern LAST_WEEK = Pattern.compile("\\blast\\s+week\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_MONTH = Pattern.compile("\\blast\\s+month\\b", Pattern.CASE_INSENSITIVE);

    public Optional<TransactionDate> resolve(String emailBody, LocalDate receptionDate) {
        if (emailBody == null || emailBody.isBlank() || receptionDate == null) {
            return Optional.empty();
        }
        boolean containsLastWeek = LAST_WEEK.matcher(emailBody).find();
        boolean containsLastMonth = LAST_MONTH.matcher(emailBody).find();

        if (containsLastWeek == containsLastMonth) {
            return Optional.empty();
        }
        if (containsLastWeek) {
            LocalDate start = receptionDate
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .minusWeeks(1);
            return Optional.of(new TransactionDate(start, start.plusDays(6)));
        }

        YearMonth previousMonth = YearMonth.from(receptionDate).minusMonths(1);
        return Optional.of(new TransactionDate(previousMonth.atDay(1), previousMonth.atEndOfMonth()));
    }
}
