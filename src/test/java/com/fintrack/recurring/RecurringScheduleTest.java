package com.fintrack.recurring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecurringScheduleTest {
    @Test
    void dailyAndWeekly() {
        assertEquals(LocalDate.of(2026, 3, 1), RecurringSchedule.nextAfter(LocalDate.of(2026, 2, 28), Frequency.DAILY, 28));
        assertEquals(LocalDate.of(2026, 1, 8), RecurringSchedule.nextAfter(LocalDate.of(2026, 1, 1), Frequency.WEEKLY, 1));
    }

    @Test
    void monthlyKeepsTheOriginalDayWhereThePossible() {
        LocalDate d = LocalDate.of(2026, 1, 31);
        d = RecurringSchedule.nextAfter(d, Frequency.MONTHLY, 31);
        assertEquals(LocalDate.of(2026, 2, 28), d);
        d = RecurringSchedule.nextAfter(d, Frequency.MONTHLY, 31);
        assertEquals(LocalDate.of(2026, 3, 31), d);
        d = RecurringSchedule.nextAfter(d, Frequency.MONTHLY, 31);
        assertEquals(LocalDate.of(2026, 4, 30), d);
    }

    @Test
    void monthlyHandlesLeapYearAndYearEnd() {
        assertEquals(LocalDate.of(2028, 2, 29), RecurringSchedule.nextAfter(LocalDate.of(2028, 1, 30), Frequency.MONTHLY, 30));
        assertEquals(LocalDate.of(2027, 1, 15), RecurringSchedule.nextAfter(LocalDate.of(2026, 12, 15), Frequency.MONTHLY, 15));
    }

    @Test
    void ruleEndsWhenNextDatePassesTheEndDate() {
        RecurringTransaction r = new RecurringTransaction();
        r.setFrequency(Frequency.DAILY);
        r.setAnchorDay(1);
        r.setEndDate(LocalDate.of(2026, 5, 2));
        r.setNextExecutionDate(LocalDate.of(2026, 5, 1));
        r.advanceAfter(LocalDate.of(2026, 5, 1));
        assertEquals(RecurringStatus.ACTIVE, r.getStatus());
        r.advanceAfter(LocalDate.of(2026, 5, 2));
        assertEquals(RecurringStatus.ENDED, r.getStatus());
    }

    @Test
    void skipToDropsMissedOccurrencesAfterAPause() {
        RecurringTransaction r = new RecurringTransaction();
        r.setFrequency(Frequency.WEEKLY);
        r.setAnchorDay(1);
        r.setNextExecutionDate(LocalDate.of(2026, 1, 1));
        r.skipTo(LocalDate.of(2026, 1, 20));
        assertEquals(LocalDate.of(2026, 1, 22), r.getNextExecutionDate());
    }
}
