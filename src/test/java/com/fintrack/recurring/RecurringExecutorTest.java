package com.fintrack.recurring;

import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.notification.NotificationService;
import com.fintrack.notification.NotificationType;
import com.fintrack.recurring.RecurringDtos.RunSummary;
import com.fintrack.transaction.TransactionDtos.TransactionRequest;
import com.fintrack.transaction.TransactionDtos.TransactionResponse;
import com.fintrack.transaction.TransactionService;
import com.fintrack.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecurringExecutorTest {
    @Mock RecurringRepository rules;
    @Mock RecurringExecutionRepository history;
    @Mock TransactionService transactions;
    @Mock NotificationService notifications;

    RecurringExecutor executor;
    RecurringTransaction rule;
    final LocalDate day = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        executor = new RecurringExecutor(rules, history, transactions, notifications);
        rule = new RecurringTransaction();
        rule.setId(5L);
        rule.setUserId(7L);
        rule.setAccountId(1L);
        rule.setCategoryId(2L);
        rule.setType(TransactionType.EXPENSE);
        rule.setAmount(new BigDecimal("8000.00"));
        rule.setDescription("Rent");
        rule.setFrequency(Frequency.MONTHLY);
        rule.setAnchorDay(10);
        rule.setStartDate(day);
        rule.setNextExecutionDate(day);
        when(rules.lockById(5L)).thenReturn(Optional.of(rule));
        when(transactions.create(eq(7L), any(TransactionRequest.class)))
                .thenReturn(new TransactionResponse(99L, 1L, "A", 2L, "Rent", TransactionType.EXPENSE, new BigDecimal("8000.00"), "Rent", null, day, null));
    }

    @Test
    void postsTheTransactionRecordsHistoryAndAdvances() {
        assertTrue(executor.execute(5L, day));
        ArgumentCaptor<TransactionRequest> req = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactions).create(eq(7L), req.capture());
        assertEquals(day, req.getValue().date());
        assertEquals(new BigDecimal("8000.00"), req.getValue().amount());
        ArgumentCaptor<RecurringExecution> h = ArgumentCaptor.forClass(RecurringExecution.class);
        verify(history).save(h.capture());
        assertEquals(ExecutionStatus.SUCCESS, h.getValue().getStatus());
        assertEquals(99L, h.getValue().getTransactionId());
        assertEquals(LocalDate.of(2026, 12, 10), rule.getNextExecutionDate());
        verify(notifications).notifyOnce(eq(7L), eq(NotificationType.RECURRING_EXECUTED), any(), any(), any());
    }

    @Test
    void doesNothingWhenTheDateIsNotTheNextExecutionDate() {
        assertFalse(executor.execute(5L, day.plusDays(1)));
        verify(transactions, never()).create(any(), any());
    }

    @Test
    void doesNotRunPausedRules() {
        rule.setStatus(RecurringStatus.PAUSED);
        assertFalse(executor.execute(5L, day));
        verify(transactions, never()).create(any(), any());
    }

    @Test
    void anExistingHistoryRowPreventsADoublePost() {
        when(history.existsByRecurringIdAndScheduledDate(5L, day)).thenReturn(true);
        assertFalse(executor.execute(5L, day));
        verify(transactions, never()).create(any(), any());
        assertEquals(LocalDate.of(2026, 12, 10), rule.getNextExecutionDate());
    }

    @Test
    void failureIsRecordedAndTheRuleMovesOn() {
        executor.recordFailure(5L, day, "Insufficient balance");
        ArgumentCaptor<RecurringExecution> h = ArgumentCaptor.forClass(RecurringExecution.class);
        verify(history).save(h.capture());
        assertEquals(ExecutionStatus.FAILED, h.getValue().getStatus());
        assertEquals(LocalDate.of(2026, 12, 10), rule.getNextExecutionDate());
        verify(notifications).notifyOnce(eq(7L), eq(NotificationType.RECURRING_FAILED), any(), any(), any());
    }

    @Test
    void runnerRecordsBusinessFailuresButNeverSkipsOnUnexpectedErrors() {
        RecurringExecutor ex = mock(RecurringExecutor.class);
        RecurringRunner runner = new RecurringRunner(rules, ex, notifications);
        when(rules.findByUserIdAndStatusAndNextExecutionDateLessThanEqual(7L, RecurringStatus.ACTIVE, day)).thenReturn(List.of(rule));
        when(rules.findById(5L)).thenReturn(Optional.of(rule));

        when(ex.execute(5L, day)).thenThrow(new InsufficientBalanceException("Insufficient balance"));
        doAnswer(i -> { rule.advanceAfter(day); return null; }).when(ex).recordFailure(5L, day, "Insufficient balance");
        RunSummary s = runner.runDue(7L, day);
        assertEquals(1, s.failed());
        verify(ex).recordFailure(5L, day, "Insufficient balance");

        rule.setNextExecutionDate(day);
        reset(ex);
        when(ex.execute(5L, day)).thenThrow(new IllegalStateException("db down"));
        RunSummary s2 = runner.runDue(7L, day);
        assertEquals(0, s2.failed());
        verify(ex, never()).recordFailure(any(), any(), any());
        assertEquals(day, rule.getNextExecutionDate());
    }
}
