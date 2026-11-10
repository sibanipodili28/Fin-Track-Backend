package com.fintrack.budget;

import com.fintrack.budget.BudgetDtos.*;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import com.fintrack.category.CategoryType;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.transaction.TransactionRepository;
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
import java.util.stream.Collectors;

/**
 * Budgets are monthly spending limits per expense category. "Spent" is always computed from real EXPENSE
 * transactions; income and transfers never count.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal EIGHTY = BigDecimal.valueOf(80);

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    @Transactional(readOnly = true)
    public BudgetOverview overview(Long userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        List<Budget> list = budgets.findByUserIdAndBudgetMonthOrderByIdAsc(userId, from);
        Map<Long, BigDecimal> spent = spentByCategory(userId, from, month.atEndOfMonth());
        Map<Long, String> names = names(list.stream().map(Budget::getCategoryId).collect(Collectors.toSet()));

        List<BudgetItem> items = list.stream()
                .map(b -> toItem(b, spent.getOrDefault(b.getCategoryId(), BigDecimal.ZERO), names.get(b.getCategoryId())))
                .toList();

        BigDecimal allocated = items.stream().map(BudgetItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = items.stream().map(BudgetItem::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> alerts = items.stream().filter(i -> i.status() != BudgetStatus.ON_TRACK).map(this::alertText).toList();

        return new BudgetOverview(month.toString(), allocated, totalSpent, allocated.subtract(totalSpent),
                percent(totalSpent, allocated), items, alerts);
    }

    @Transactional
    public BudgetItem create(Long userId, CreateBudgetRequest r) {
        YearMonth month = YearMonth.parse(r.month());
        Category cat = categories.findVisible(r.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (cat.getType() != CategoryType.EXPENSE) {
            throw new BusinessRuleException("Budgets can only be set for expense categories");
        }
        LocalDate from = month.atDay(1);
        if (budgets.existsByUserIdAndCategoryIdAndBudgetMonth(userId, cat.getId(), from)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "A budget for " + cat.getName() + " already exists for " + month);
        }
        Budget b = new Budget();
        b.setUserId(userId);
        b.setCategoryId(cat.getId());
        b.setBudgetMonth(from);
        b.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        b = budgets.save(b);
        BigDecimal spent = spentByCategory(userId, from, month.atEndOfMonth()).getOrDefault(cat.getId(), BigDecimal.ZERO);
        return toItem(b, spent, cat.getName());
    }

    @Transactional
    public BudgetItem update(Long userId, Long id, UpdateBudgetRequest r) {
        Budget b = find(userId, id);
        b.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        YearMonth month = YearMonth.from(b.getBudgetMonth());
        BigDecimal spent = spentByCategory(userId, month.atDay(1), month.atEndOfMonth()).getOrDefault(b.getCategoryId(), BigDecimal.ZERO);
        String name = categories.findById(b.getCategoryId()).map(Category::getName).orElse("");
        return toItem(b, spent, name);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        budgets.delete(find(userId, id));
    }

    /** Allocated vs spent per month for the last {@code months} months (including the current one). */
    @Transactional(readOnly = true)
    public List<BudgetHistoryPoint> history(Long userId, int months) {
        int n = Math.min(Math.max(months, 1), 24);
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(n - 1L);
        List<Budget> list = budgets.findByUserIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(userId, start.atDay(1), end.atDay(1));

        Map<String, BigDecimal> spentByMonthAndCategory = new HashMap<>();
        for (Object[] r : transactions.monthlyTotalsByCategory(userId, TransactionType.EXPENSE, start.atDay(1), end.atEndOfMonth())) {
            YearMonth m = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            spentByMonthAndCategory.put(m + "|" + r[2], (BigDecimal) r[3]);
        }
        List<BudgetHistoryPoint> out = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            BigDecimal allocated = BigDecimal.ZERO;
            BigDecimal spent = BigDecimal.ZERO;
            for (Budget b : list) {
                if (!YearMonth.from(b.getBudgetMonth()).equals(m)) continue;
                allocated = allocated.add(b.getAmount());
                spent = spent.add(spentByMonthAndCategory.getOrDefault(m + "|" + b.getCategoryId(), BigDecimal.ZERO));
            }
            out.add(new BudgetHistoryPoint(m.toString(), allocated, spent));
        }
        return out;
    }

    /** 80% or more is NEAR_LIMIT, exactly 100% is LIMIT_REACHED, above 100% is EXCEEDED. */
    static BudgetStatus statusOf(BigDecimal spent, BigDecimal amount) {
        int cmp = spent.compareTo(amount);
        if (cmp > 0) return BudgetStatus.EXCEEDED;
        if (cmp == 0) return BudgetStatus.LIMIT_REACHED;
        if (spent.multiply(HUNDRED).compareTo(amount.multiply(EIGHTY)) >= 0) return BudgetStatus.NEAR_LIMIT;
        return BudgetStatus.ON_TRACK;
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private BudgetItem toItem(Budget b, BigDecimal spent, String name) {
        return new BudgetItem(b.getId(), b.getCategoryId(), name, b.getAmount(), spent, b.getAmount().subtract(spent),
                percent(spent, b.getAmount()), statusOf(spent, b.getAmount()));
    }

    private String alertText(BudgetItem i) {
        return switch (i.status()) {
            case NEAR_LIMIT -> i.categoryName() + ": " + i.utilizationPercent() + "% of the budget is used";
            case LIMIT_REACHED -> i.categoryName() + ": the budget is fully used";
            case EXCEEDED -> i.categoryName() + ": over budget by " + i.spent().subtract(i.amount());
            default -> "";
        };
    }

    private Map<Long, BigDecimal> spentByCategory(Long userId, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] r : transactions.totalsByCategoryId(userId, TransactionType.EXPENSE, from, to)) {
            map.put((Long) r[0], (BigDecimal) r[1]);
        }
        return map;
    }

    private Map<Long, String> names(Set<Long> ids) {
        return categories.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private Budget find(Long userId, Long id) {
        return budgets.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
    }
}
