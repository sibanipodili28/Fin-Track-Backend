package com.fintrack.analytics;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.analytics.AnalyticsDtos.*;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.DateRanges;
import com.fintrack.dashboard.DashboardDtos.CategoryTotal;
import com.fintrack.dashboard.DashboardService;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Aggregations are done by the database (GROUP BY), never by loading every transaction. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final DashboardService dashboard;

    @Transactional(readOnly = true)
    public AnalyticsOverview overview(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? YearMonth.now().atEndOfMonth() : to;
        LocalDate start = from == null ? YearMonth.from(end).minusMonths(5).atDay(1) : from;
        DateRanges.validate(start, end);

        List<MonthPoint> monthly = dashboard.trend(userId, start, end).stream()
                .map(p -> new MonthPoint(p.month(), p.income(), p.expense(), p.income().subtract(p.expense()))).toList();
        List<CategoryTotal> categories = transactions.totalsByCategory(userId, TransactionType.EXPENSE, start, end).stream()
                .map(r -> new CategoryTotal((String) r[0], (BigDecimal) r[1])).toList();
        List<AccountBalance> balances = accounts.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(Account::isActive)
                .map(a -> new AccountBalance(a.getName(), a.getType().name(), a.getCurrentBalance(), a.isCreditCard())).toList();
        return new AnalyticsOverview(start, end, monthly, categories, balances);
    }

    @Transactional(readOnly = true)
    public YearComparison yearComparison(Long userId, Integer yearParam) {
        int year = yearParam == null ? LocalDate.now().getYear() : yearParam;
        if (year < 2001 || year > 2100) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Year must be between 2001 and 2100");
        BigDecimal[] current = new BigDecimal[12];
        BigDecimal[] previous = new BigDecimal[12];
        Arrays.fill(current, BigDecimal.ZERO);
        Arrays.fill(previous, BigDecimal.ZERO);
        for (Object[] r : transactions.monthlyTotals(userId, LocalDate.of(year - 1, 1, 1), LocalDate.of(year, 12, 31))) {
            if (r[2] != TransactionType.EXPENSE) continue;
            int m = ((Number) r[1]).intValue() - 1;
            if (((Number) r[0]).intValue() == year) current[m] = (BigDecimal) r[3]; else previous[m] = (BigDecimal) r[3];
        }
        List<YearPoint> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            points.add(new YearPoint(Month.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), current[i], previous[i]));
        }
        return new YearComparison(year, year - 1, points);
    }
}
