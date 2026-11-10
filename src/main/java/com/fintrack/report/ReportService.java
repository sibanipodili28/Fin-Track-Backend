package com.fintrack.report;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.budget.BudgetDtos.BudgetItem;
import com.fintrack.budget.BudgetDtos.BudgetOverview;
import com.fintrack.budget.BudgetService;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.DateRanges;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.goal.GoalDtos.GoalResponse;
import com.fintrack.goal.GoalService;
import com.fintrack.report.ReportData.SummaryItem;
import com.fintrack.transaction.TransactionRecord;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import com.fintrack.transfer.Transfer;
import com.fintrack.transfer.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Builds every report from the authenticated user's own data only. */
@Service
@RequiredArgsConstructor
public class ReportService {
    static final int MAX_STATEMENT_ROWS = 20_000;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record ReportParams(YearMonth month, Integer year, LocalDate from, LocalDate to, Long accountId) {}

    private final TransactionRepository transactions;
    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final BudgetService budgetService;
    private final GoalService goalService;

    @Transactional(readOnly = true)
    public ReportData generate(Long userId, ReportType type, ReportParams p) {
        return switch (type) {
            case MONTHLY -> monthly(userId, p.month() == null ? YearMonth.now() : p.month());
            case YEARLY -> yearly(userId, p.year() == null ? LocalDate.now().getYear() : p.year());
            case CATEGORY -> category(userId, p.from(), p.to());
            case ACCOUNT_STATEMENT -> statement(userId, p.accountId(), p.from(), p.to());
            case BUDGET -> budget(userId, p.month() == null ? YearMonth.now() : p.month());
            case SAVINGS -> savings(userId);
        };
    }

    // ---------------------------------------------------------------- monthly
    ReportData monthly(Long userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = nz(transactions.sumByType(userId, TransactionType.INCOME, from, to));
        BigDecimal expense = nz(transactions.sumByType(userId, TransactionType.EXPENSE, from, to));

        List<List<Object>> rows = new ArrayList<>();
        addCategoryRows(rows, "Income", transactions.totalsByCategory(userId, TransactionType.INCOME, from, to), income);
        addCategoryRows(rows, "Expense", transactions.totalsByCategory(userId, TransactionType.EXPENSE, from, to), expense);

        BigDecimal net = income.subtract(expense);
        return new ReportData("Monthly report: " + month, "From " + from + " to " + to,
                List.of("Type", "Category", "Amount", "Share of type"), rows,
                List.of(new SummaryItem("Total income", income), new SummaryItem("Total expenses", expense),
                        new SummaryItem("Net savings", net), new SummaryItem("Savings rate", pct(net, income))));
    }

    // ----------------------------------------------------------------- yearly
    ReportData yearly(Long userId, int year) {
        if (year < 2000 || year > 2100) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Year must be between 2000 and 2100");
        BigDecimal[] income = new BigDecimal[12];
        BigDecimal[] expense = new BigDecimal[12];
        Arrays.fill(income, BigDecimal.ZERO);
        Arrays.fill(expense, BigDecimal.ZERO);
        for (Object[] r : transactions.monthlyTotals(userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            int m = ((Number) r[1]).intValue() - 1;
            if (r[2] == TransactionType.INCOME) income[m] = (BigDecimal) r[3]; else expense[m] = (BigDecimal) r[3];
        }
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            BigDecimal net = income[i].subtract(expense[i]);
            rows.add(row(Month.of(i + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH), income[i], expense[i], net, pct(net, income[i])));
            totalIncome = totalIncome.add(income[i]);
            totalExpense = totalExpense.add(expense[i]);
        }
        BigDecimal net = totalIncome.subtract(totalExpense);
        return new ReportData("Yearly summary: " + year, "January to December " + year,
                List.of("Month", "Income", "Expenses", "Net savings", "Savings rate"), rows,
                List.of(new SummaryItem("Total income", totalIncome), new SummaryItem("Total expenses", totalExpense),
                        new SummaryItem("Net savings", net), new SummaryItem("Savings rate", pct(net, totalIncome))));
    }

    // --------------------------------------------------------------- category
    ReportData category(Long userId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? YearMonth.now().atDay(1) : from;
        LocalDate end = to == null ? YearMonth.now().atEndOfMonth() : to;
        DateRanges.validate(start, end);
        BigDecimal total = nz(transactions.sumByType(userId, TransactionType.EXPENSE, start, end));
        List<List<Object>> rows = new ArrayList<>();
        for (Object[] r : transactions.totalsByCategory(userId, TransactionType.EXPENSE, start, end)) {
            rows.add(row(r[0], r[1], pct((BigDecimal) r[1], total)));
        }
        return new ReportData("Category-wise spending", "From " + start + " to " + end, List.of("Category", "Amount", "Share of spending"), rows,
                List.of(new SummaryItem("Total spending", total), new SummaryItem("Categories", BigDecimal.valueOf(rows.size()).toPlainString())));
    }

    // -------------------------------------------------------------- statement
    ReportData statement(Long userId, Long accountId, LocalDate from, LocalDate to) {
        if (accountId == null) throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "accountId is required");
        Account acc = accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        LocalDate start = from == null ? YearMonth.now().atDay(1) : from;
        LocalDate end = to == null ? YearMonth.now().atEndOfMonth() : to;
        DateRanges.validate(start, end);

        // Opening balance = initial balance + everything that happened before the first day.
        LocalDateTime startTime = start.atStartOfDay();
        BigDecimal opening = acc.getInitialBalance();
        for (Object[] r : transactions.totalsBeforeForAccount(userId, accountId, start)) {
            opening = opening.add(acc.effectOf((TransactionType) r[0], (BigDecimal) r[1]));
        }
        opening = opening.subtract(nz(transfers.sumOutBefore(userId, accountId, startTime)));
        BigDecimal in = nz(transfers.sumInBefore(userId, accountId, startTime));
        opening = opening.add(acc.isCreditCard() ? in.negate() : in);

        var limit = PageRequest.of(0, MAX_STATEMENT_ROWS + 1);
        List<TransactionRecord> txs = transactions.findByUserIdAndAccountIdAndTxnDateBetweenOrderByTxnDateAscIdAsc(userId, accountId, start, end, limit);
        List<Transfer> trs = transfers.findForStatement(userId, accountId, startTime, end.plusDays(1).atStartOfDay(), limit);
        if (txs.size() > MAX_STATEMENT_ROWS || trs.size() > MAX_STATEMENT_ROWS) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Too many entries for one statement. Choose a shorter date range.");
        }

        Map<Long, String> categoryNames = categories.findAllById(txs.stream().map(TransactionRecord::getCategoryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        Set<Long> otherIds = new HashSet<>();
        trs.forEach(t -> { otherIds.add(t.getFromAccountId()); otherIds.add(t.getToAccountId()); });
        Map<Long, String> accountNames = accounts.findAllById(otherIds).stream().collect(Collectors.toMap(Account::getId, Account::getName));

        List<Entry> entries = new ArrayList<>();
        for (TransactionRecord t : txs) {
            String label = (t.getDescription() == null || t.getDescription().isBlank()) ? categoryNames.getOrDefault(t.getCategoryId(), "") : t.getDescription();
            entries.add(new Entry(t.getTxnDate(), 0, t.getId(), label, t.getType() == TransactionType.INCOME ? "Income" : "Expense",
                    acc.effectOf(t.getType(), t.getAmount())));
        }
        for (Transfer t : trs) {
            boolean outgoing = t.getFromAccountId().equals(accountId);
            BigDecimal change = outgoing ? t.getAmount().negate() : (acc.isCreditCard() ? t.getAmount().negate() : t.getAmount());
            String other = accountNames.getOrDefault(outgoing ? t.getToAccountId() : t.getFromAccountId(), "");
            entries.add(new Entry(t.getCreatedAt().toLocalDate(), 1, t.getId(), (outgoing ? "Transfer to " : "Transfer from ") + other, "Transfer", change));
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparingInt(Entry::order).thenComparing(Entry::id));

        List<List<Object>> rows = new ArrayList<>();
        BigDecimal balance = opening;
        BigDecimal increases = BigDecimal.ZERO;
        BigDecimal decreases = BigDecimal.ZERO;
        for (Entry e : entries) {
            balance = balance.add(e.change());
            if (e.change().signum() >= 0) increases = increases.add(e.change()); else decreases = decreases.add(e.change().negate());
            rows.add(row(e.date().toString(), e.description(), e.type(), e.change(), balance));
        }
        String note = acc.isCreditCard() ? " Credit card: balance is the amount owed; a positive change means a higher balance owed." : "";
        return new ReportData("Account statement: " + acc.getName(), "From " + start + " to " + end + " (" + acc.getCurrency() + ")." + note,
                List.of("Date", "Description", "Type", "Change", "Balance"), rows,
                List.of(new SummaryItem("Opening balance", opening), new SummaryItem("Total increases", increases),
                        new SummaryItem("Total decreases", decreases), new SummaryItem("Closing balance", balance)));
    }

    // ----------------------------------------------------------------- budget
    ReportData budget(Long userId, YearMonth month) {
        BudgetOverview o = budgetService.overview(userId, month);
        List<List<Object>> rows = new ArrayList<>();
        for (BudgetItem i : o.items()) {
            rows.add(row(i.categoryName(), i.amount(), i.spent(), i.remaining(), i.utilizationPercent().toPlainString() + "%", label(i.status().name())));
        }
        return new ReportData("Budget utilization: " + month, null, List.of("Category", "Budget", "Spent", "Remaining", "Used", "Status"), rows,
                List.of(new SummaryItem("Total allocated", o.totalAllocated()), new SummaryItem("Total spent", o.totalSpent()),
                        new SummaryItem("Total remaining", o.totalRemaining()), new SummaryItem("Overall utilization", o.utilizationPercent().toPlainString() + "%")));
    }

    // ---------------------------------------------------------------- savings
    ReportData savings(Long userId) {
        List<GoalResponse> goals = goalService.list(userId);
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal target = BigDecimal.ZERO;
        BigDecimal saved = BigDecimal.ZERO;
        int active = 0;
        for (GoalResponse g : goals) {
            rows.add(row(g.name(), g.targetAmount(), g.savedAmount(), g.remainingAmount(), g.progressPercent().toPlainString() + "%",
                    g.targetDate() == null ? "" : g.targetDate().toString(), g.overdue() ? "Overdue" : label(g.status().name())));
            target = target.add(g.targetAmount());
            saved = saved.add(g.savedAmount());
            if (g.status().name().equals("ACTIVE")) active++;
        }
        return new ReportData("Savings progress", "As of " + LocalDate.now(), List.of("Goal", "Target", "Saved", "Remaining", "Progress", "Target date", "Status"), rows,
                List.of(new SummaryItem("Total target", target), new SummaryItem("Total saved", saved),
                        new SummaryItem("Active goals", String.valueOf(active))));
    }

    // ---------------------------------------------------------------- helpers
    private record Entry(LocalDate date, int order, Long id, String description, String type, BigDecimal change) {}

    private void addCategoryRows(List<List<Object>> rows, String type, List<Object[]> totals, BigDecimal typeTotal) {
        for (Object[] r : totals) rows.add(row(type, r[0], r[1], pct((BigDecimal) r[1], typeTotal)));
    }

    private static List<Object> row(Object... cells) { return new ArrayList<>(Arrays.asList(cells)); }

    static String pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "0.0%";
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String label(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
