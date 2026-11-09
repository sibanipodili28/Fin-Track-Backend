package com.fintrack.recurring;

import com.fintrack.notification.NotificationService;
import com.fintrack.notification.NotificationType;
import com.fintrack.transaction.TransactionDtos.TransactionRequest;
import com.fintrack.transaction.TransactionDtos.TransactionResponse;
import com.fintrack.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Executes ONE occurrence of a rule in ONE database transaction. The rule row is locked, and the
 * (rule, date) pair is unique in recurring_execution_history, so an occurrence can never post twice,
 * even if two instances run at the same moment. If anything fails, the whole occurrence rolls back.
 */
@Component
@RequiredArgsConstructor
public class RecurringExecutor {
    private final RecurringRepository rules;
    private final RecurringExecutionRepository history;
    private final TransactionService transactions;
    private final NotificationService notifications;

    /** @return true if a transaction was posted. */
    @Transactional
    public boolean execute(Long ruleId, LocalDate date) {
        RecurringTransaction r = rules.lockById(ruleId).orElse(null);
        if (r == null || r.getStatus() != RecurringStatus.ACTIVE || !date.equals(r.getNextExecutionDate())) return false;
        if (history.existsByRecurringIdAndScheduledDate(ruleId, date)) {
            r.advanceAfter(date);
            return false;
        }
        TransactionResponse t = transactions.create(r.getUserId(), new TransactionRequest(
                r.getAccountId(), r.getCategoryId(), r.getType(), r.getAmount(), date, r.getDescription(), "Posted by recurring rule #" + r.getId()));

        RecurringExecution e = new RecurringExecution();
        e.setRecurringId(ruleId);
        e.setUserId(r.getUserId());
        e.setScheduledDate(date);
        e.setStatus(ExecutionStatus.SUCCESS);
        e.setTransactionId(t.id());
        history.save(e);

        r.advanceAfter(date);
        notifications.notifyOnce(r.getUserId(), NotificationType.RECURRING_EXECUTED, "Recurring " + r.getType().name().toLowerCase() + " posted",
                label(r) + " of " + r.getAmount() + " was posted for " + date + ".", "RECUR_OK:" + ruleId + ":" + date);
        return true;
    }

    /** Records a business-rule failure (for example insufficient balance) and moves on to the next date. */
    @Transactional
    public void recordFailure(Long ruleId, LocalDate date, String reason) {
        RecurringTransaction r = rules.lockById(ruleId).orElse(null);
        if (r == null || r.getStatus() != RecurringStatus.ACTIVE || !date.equals(r.getNextExecutionDate())) return;
        if (!history.existsByRecurringIdAndScheduledDate(ruleId, date)) {
            RecurringExecution e = new RecurringExecution();
            e.setRecurringId(ruleId);
            e.setUserId(r.getUserId());
            e.setScheduledDate(date);
            e.setStatus(ExecutionStatus.FAILED);
            e.setMessage(reason.length() > 255 ? reason.substring(0, 255) : reason);
            history.save(e);
        }
        r.advanceAfter(date);
        notifications.notifyOnce(r.getUserId(), NotificationType.RECURRING_FAILED, "Recurring payment failed",
                label(r) + " for " + date + " was not posted: " + reason, "RECUR_FAIL:" + ruleId + ":" + date);
    }

    private static String label(RecurringTransaction r) {
        return r.getDescription() == null || r.getDescription().isBlank() ? "Recurring " + r.getType().name().toLowerCase() : r.getDescription();
    }
}
