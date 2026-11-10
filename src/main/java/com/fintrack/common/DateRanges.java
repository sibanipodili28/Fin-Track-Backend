package com.fintrack.common;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;

public final class DateRanges {
    private DateRanges() {}

    /** Rejects reversed ranges and ranges longer than 5 years, so reports and charts stay fast. */
    public static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        if (from.plusYears(5).isBefore(to)) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Date range is limited to 5 years");
    }
}
