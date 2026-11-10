package com.fintrack.dashboard;

import com.fintrack.account.Account;
import com.fintrack.account.AccountDtos.AccountResponse;
import com.fintrack.account.AccountRepository;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.dashboard.DashboardDtos.*;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionService;
import com.fintrack.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long userId, DashboardRange range, LocalDate customFrom, LocalDate customTo) {
        LocalDate[] period = resolve(range, customFrom, customTo);
        LocalDate from = period[0];
        LocalDate to = period[1];

        BigDecimal income = nz(transactions.sumByType(userId, TransactionType.INCOME, from, to));
        BigDecimal expense = nz(transactions.sumByType(userId, TransactionType.EXPENSE, from, to));
        BigDecimal net = income.subtract(expense);
        BigDecimal rate = income.signum() > 0
                ? net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1);

        List<Account> userAccounts = accounts.findByUserIdOrderByCreatedAtDesc(userId);
        // Net worth: assets held minus credit-card balances owed (active accounts only).
        BigDecimal total = userAccounts.stream().filter(Account::isActive)
                .map(a -> a.isCreditCard() ? a.getCurrentBalance().negate() : a.getCurrentBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryTotal> byCategory = transactions.totalsByCategory(userId, TransactionType.EXPENSE, from, to).stream()
                .map(r -> new CategoryTotal((String) r[0], (BigDecimal) r[1])).toList();

        return new DashboardSummary(from, to, total, income, expense, net, rate,
                userAccounts.stream().map(AccountResponse::from).toList(),
                transactionService.toResponses(transactions.findTop5ByUserIdOrderByTxnDateDescIdDesc(userId)),
                byCategory, trend(userId, from, to));
    }

    public List<MonthlyPoint> trend(Long userId, LocalDate from, LocalDate to) {
        Map<YearMonth, BigDecimal[]> buckets = new TreeMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            buckets.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Object[] r : transactions.monthlyTotals(userId, from, to)) {
            YearMonth m = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            BigDecimal[] b = buckets.get(m);
            if (b == null) continue;
            b[r[2] == TransactionType.INCOME ? 0 : 1] = (BigDecimal) r[3];
        }
        return buckets.entrySet().stream()
                .map(e -> new MonthlyPoint(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();
    }

    static LocalDate[] resolve(DashboardRange range, LocalDate customFrom, LocalDate customTo) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return switch (range) {
            case CURRENT_MONTH -> new LocalDate[]{monthStart, monthStart.plusMonths(1).minusDays(1)};
            case PREVIOUS_MONTH -> new LocalDate[]{monthStart.minusMonths(1), monthStart.minusDays(1)};
            case LAST_3_MONTHS -> new LocalDate[]{monthStart.minusMonths(2), monthStart.plusMonths(1).minusDays(1)};
            case LAST_6_MONTHS -> new LocalDate[]{monthStart.minusMonths(5), monthStart.plusMonths(1).minusDays(1)};
            case CURRENT_YEAR -> new LocalDate[]{today.withDayOfYear(1), LocalDate.of(today.getYear(), 12, 31)};
            case CUSTOM -> {
                if (customFrom == null || customTo == null) {
                    throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Custom range needs both 'from' and 'to'");
                }
                if (customFrom.isAfter(customTo)) {
                    throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
                }
                if (customFrom.plusYears(5).isBefore(customTo)) {
                    throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Custom range is limited to 5 years");
                }
                yield new LocalDate[]{customFrom, customTo};
            }
        };
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO.setScale(2) : v; }
}
