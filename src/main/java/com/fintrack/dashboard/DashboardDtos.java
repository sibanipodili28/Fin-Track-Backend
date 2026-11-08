package com.fintrack.dashboard;

import com.fintrack.account.AccountDtos.AccountResponse;
import com.fintrack.transaction.TransactionDtos.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record CategoryTotal(String category, BigDecimal amount) {}

    public record MonthlyPoint(String month, BigDecimal income, BigDecimal expense) {}

    public record DashboardSummary(LocalDate from, LocalDate to, BigDecimal totalBalance, BigDecimal income,
                                   BigDecimal expense, BigDecimal netSavings, BigDecimal savingsRate,
                                   List<AccountResponse> accounts, List<TransactionResponse> recentTransactions,
                                   List<CategoryTotal> expenseByCategory, List<MonthlyPoint> monthlyTrend) {}
}
