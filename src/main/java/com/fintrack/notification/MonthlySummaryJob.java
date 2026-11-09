package com.fintrack.notification;

import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;

/** On the 1st of each month, tells every user how the previous month went. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySummaryJob {
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final NotificationService notifications;

    @Scheduled(cron = "${app.summary.cron:0 30 6 1 * *}")
    public void run() {
        YearMonth previous = YearMonth.now().minusMonths(1);
        int page = 0;
        Page<User> batch;
        do {
            batch = users.findAll(PageRequest.of(page++, 200));
            for (User u : batch) {
                try {
                    summarize(u.getId(), previous);
                } catch (RuntimeException e) {
                    log.warn("Monthly summary failed for user {}", u.getId(), e);
                }
            }
        } while (batch.hasNext());
    }

    void summarize(Long userId, YearMonth month) {
        BigDecimal income = nz(transactions.sumByType(userId, TransactionType.INCOME, month.atDay(1), month.atEndOfMonth()));
        BigDecimal expense = nz(transactions.sumByType(userId, TransactionType.EXPENSE, month.atDay(1), month.atEndOfMonth()));
        if (income.signum() == 0 && expense.signum() == 0) return;
        BigDecimal net = income.subtract(expense);
        notifications.notifyOnce(userId, NotificationType.MONTHLY_SUMMARY, "Your " + month + " summary",
                "Income " + income + ", expenses " + expense + ", " + (net.signum() >= 0 ? "saved " + net : "overspent by " + net.negate()) + ".",
                "SUMMARY:" + month);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
