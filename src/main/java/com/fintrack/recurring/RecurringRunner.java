package com.fintrack.recurring;

import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.notification.NotificationService;
import com.fintrack.notification.NotificationType;
import com.fintrack.recurring.RecurringDtos.RunSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Finds due rules and runs them one occurrence at a time. Not transactional itself on purpose. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringRunner {
    static final int MAX_CATCH_UP = 31;

    private final RecurringRepository rules;
    private final RecurringExecutor executor;
    private final NotificationService notifications;

    @Scheduled(cron = "${app.recurring.cron:0 5 0 * * *}")
    public void scheduledRun() {
        LocalDate today = LocalDate.now();
        RunSummary s = runDue(null, today);
        log.info("Recurring run: {} posted, {} failed", s.executed(), s.failed());
        notifyUpcoming(today);
    }

    /** @param userId limit to one user, or null for everyone. Missed days are caught up, at most 31 per rule per run. */
    public RunSummary runDue(Long userId, LocalDate today) {
        List<RecurringTransaction> due = userId == null
                ? rules.findByStatusAndNextExecutionDateLessThanEqual(RecurringStatus.ACTIVE, today)
                : rules.findByUserIdAndStatusAndNextExecutionDateLessThanEqual(userId, RecurringStatus.ACTIVE, today);
        int executed = 0;
        int failed = 0;
        for (RecurringTransaction rule : due) {
            for (int i = 0; i < MAX_CATCH_UP; i++) {
                RecurringTransaction current = rules.findById(rule.getId()).orElse(null);
                if (current == null || current.getStatus() != RecurringStatus.ACTIVE || current.getNextExecutionDate().isAfter(today)) break;
                LocalDate date = current.getNextExecutionDate();
                try {
                    if (executor.execute(current.getId(), date)) executed++;
                } catch (BusinessRuleException | InsufficientBalanceException | ResourceNotFoundException e) {
                    failed++;
                    executor.recordFailure(current.getId(), date, e.getMessage());
                } catch (RuntimeException e) {
                    // Unexpected (for example the database is down): leave the rule untouched so the next run retries.
                    log.error("Recurring rule {} failed unexpectedly for {}", current.getId(), date, e);
                    break;
                }
            }
        }
        return new RunSummary(executed, failed);
    }

    /** Heads-up for payments due in the next 1 to 3 days. */
    public void notifyUpcoming(LocalDate today) {
        for (RecurringTransaction r : rules.findByStatusAndNextExecutionDateBetween(RecurringStatus.ACTIVE, today.plusDays(1), today.plusDays(3))) {
            String what = r.getDescription() == null || r.getDescription().isBlank() ? "A recurring " + r.getType().name().toLowerCase() : r.getDescription();
            notifications.notifyOnce(r.getUserId(), NotificationType.RECURRING_UPCOMING, "Upcoming recurring payment",
                    what + " of " + r.getAmount() + " is due on " + r.getNextExecutionDate() + ".",
                    "RECUR_UP:" + r.getId() + ":" + r.getNextExecutionDate());
        }
    }
}
