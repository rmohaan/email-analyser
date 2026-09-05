package com.example.financialemail.workflow;

import com.example.financialemail.domain.TransactionDate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DateWindowPlanner {
    private static final int DEFAULT_LOOKBACK_DAYS = 7;
    private static final int EXPANSION_DAYS = 30;

    public SearchWindow initial(TransactionDate extractedDate, LocalDate receptionDate) {
        if (extractedDate == null || extractedDate.fromDate() == null || extractedDate.toDate() == null) {
            return new SearchWindow(receptionDate.minusDays(DEFAULT_LOOKBACK_DAYS), receptionDate);
        }
        return new SearchWindow(extractedDate.fromDate(), extractedDate.toDate());
    }

    public List<PlannedWindow> expanded(SearchWindow initial, LocalDate processingDate) {
        List<PlannedWindow> windows = new ArrayList<>();
        windows.add(new PlannedWindow(SearchPhase.BACKWARD_30_DAYS,
                new SearchWindow(initial.fromDate().minusDays(EXPANSION_DAYS),
                        initial.fromDate().minusDays(1))));

        LocalDate forwardStart = initial.toDate().plusDays(1);
        LocalDate forwardEnd = initial.toDate().plusDays(EXPANSION_DAYS);
        if (forwardEnd.isAfter(processingDate)) {
            forwardEnd = processingDate;
        }
        if (!forwardStart.isAfter(forwardEnd)) {
            windows.add(new PlannedWindow(SearchPhase.FORWARD_30_DAYS,
                    new SearchWindow(forwardStart, forwardEnd)));
        }
        return List.copyOf(windows);
    }

    public SearchWindow breadth(SearchWindow initial, LocalDate processingDate) {
        LocalDate end = initial.toDate().plusDays(EXPANSION_DAYS);
        if (end.isAfter(processingDate)) {
            end = processingDate;
        }
        if (end.isBefore(initial.toDate())) {
            end = initial.toDate();
        }
        return new SearchWindow(initial.fromDate().minusDays(EXPANSION_DAYS), end);
    }

    public record PlannedWindow(SearchPhase phase, SearchWindow window) {
    }
}
