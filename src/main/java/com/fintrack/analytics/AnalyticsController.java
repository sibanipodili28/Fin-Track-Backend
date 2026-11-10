package com.fintrack.analytics;

import com.fintrack.analytics.AnalyticsDtos.AnalyticsOverview;
import com.fintrack.analytics.AnalyticsDtos.YearComparison;
import com.fintrack.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService service;

    /** Without from/to this covers the last 6 months including the current one. */
    @GetMapping("/overview")
    public AnalyticsOverview overview(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(me.id(), from, to);
    }

    @GetMapping("/yearly-comparison")
    public YearComparison yearly(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.yearComparison(me.id(), year);
    }
}
