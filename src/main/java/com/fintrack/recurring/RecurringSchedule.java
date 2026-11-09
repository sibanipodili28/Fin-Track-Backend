package com.fintrack.recurring;

import java.time.LocalDate;
import java.time.YearMonth;

/** Date arithmetic for recurrence rules. */
public final class RecurringSchedule {
    private RecurringSchedule() {}

    /**
     * The occurrence after {@code current}. Monthly rules keep the original day of month where possible
     * (31 Jan -> 28/29 Feb -> 31 Mar) instead of drifting to the 28th forever.
     */
    public static LocalDate nextAfter(LocalDate current, Frequency frequency, int anchorDay) {
        return switch (frequency) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> {
                YearMonth next = YearMonth.from(current).plusMonths(1);
                yield next.atDay(Math.min(anchorDay, next.lengthOfMonth()));
            }
        };
    }
}
