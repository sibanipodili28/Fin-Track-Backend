package com.fintrack.budget;

import com.fintrack.budget.BudgetDtos.BudgetStatus;
import com.fintrack.category.CategoryRepository;
import com.fintrack.notification.NotificationService;
import com.fintrack.notification.NotificationType;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Creates one notification per budget and status (80%, 100%, exceeded) after an expense is saved. */
@Service
@RequiredArgsConstructor
public class BudgetAlertService {
    private final BudgetRepository budgets;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final NotificationService notifications;

    @Transactional
    public void checkAfterExpense(Long userId, Long categoryId, LocalDate date) {
        LocalDate monthStart = date.withDayOfMonth(1);
        budgets.findByUserIdAndCategoryIdAndBudgetMonth(userId, categoryId, monthStart).ifPresent(b -> {
            BigDecimal spent = BigDecimal.ZERO;
            for (Object[] r : transactions.totalsByCategoryId(userId, TransactionType.EXPENSE, monthStart, YearMonth.from(date).atEndOfMonth())) {
                if (categoryId.equals(r[0])) spent = (BigDecimal) r[1];
            }
            BudgetStatus status = BudgetService.statusOf(spent, b.getAmount());
            if (status == BudgetStatus.ON_TRACK) return;

            String name = categories.findById(categoryId).map(c -> c.getName()).orElse("Category");
            String month = YearMonth.from(monthStart).toString();
            String key = "BUDGET:" + b.getId() + ":" + status;
            BigDecimal pct = BudgetService.percent(spent, b.getAmount());
            switch (status) {
                case NEAR_LIMIT -> notifications.notifyOnce(userId, NotificationType.BUDGET_NEAR_LIMIT, name + " budget at " + pct + "%",
                        "You have used " + spent + " of the " + b.getAmount() + " " + name + " budget for " + month + ".", key);
                case LIMIT_REACHED -> notifications.notifyOnce(userId, NotificationType.BUDGET_LIMIT_REACHED, name + " budget fully used",
                        "You have spent the whole " + b.getAmount() + " " + name + " budget for " + month + ".", key);
                case EXCEEDED -> notifications.notifyOnce(userId, NotificationType.BUDGET_EXCEEDED, name + " budget exceeded",
                        "You are " + spent.subtract(b.getAmount()) + " over the " + name + " budget for " + month + ".", key);
                default -> { }
            }
        });
    }
}
