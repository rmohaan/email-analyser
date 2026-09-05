package com.example.financialemail.workflow;

import com.example.financialemail.domain.TransactionDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateWindowPlannerTest {
    private final DateWindowPlanner planner = new DateWindowPlanner();

    @Test
    void usesASevenDayLookbackWhenTheEmailHasNoDate() {
        assertThat(planner.initial(new TransactionDate(null, null), LocalDate.of(2026, 9, 5)))
                .isEqualTo(new SearchWindow(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 5)));
    }

    @Test
    void createsAdjacentWindowsAndCapsTheForwardWindowAtTheProcessingDate() {
        SearchWindow initial = new SearchWindow(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30));

        assertThat(planner.expanded(initial, LocalDate.of(2026, 9, 5)))
                .containsExactly(
                        new DateWindowPlanner.PlannedWindow(SearchPhase.BACKWARD_30_DAYS,
                                new SearchWindow(LocalDate.of(2026, 7, 25), LocalDate.of(2026, 8, 23))),
                        new DateWindowPlanner.PlannedWindow(SearchPhase.FORWARD_30_DAYS,
                                new SearchWindow(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 5))));
    }

    @Test
    void omitsTheForwardWindowWhenItWouldBeInTheFuture() {
        SearchWindow initial = new SearchWindow(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 5));

        assertThat(planner.expanded(initial, LocalDate.of(2026, 9, 5)))
                .extracting(DateWindowPlanner.PlannedWindow::phase)
                .containsExactly(SearchPhase.BACKWARD_30_DAYS);
    }
}
