package com.fintrack.analytics;

import com.fintrack.dashboard.DashboardDtos.CategoryTotal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record MonthPoint(String month, BigDecimal income, BigDecimal expense, BigDecimal savings) {}

    public record AccountBalance(String name, String type, BigDecimal balance, boolean creditCard) {}

    public record AnalyticsOverview(LocalDate from, LocalDate to, List<MonthPoint> monthly, List<CategoryTotal> expenseByCategory,
                                    List<AccountBalance> accountBalances) {}

    public record YearPoint(String month, BigDecimal current, BigDecimal previous) {}

    public record YearComparison(int year, int previousYear, List<YearPoint> months) {}
}
