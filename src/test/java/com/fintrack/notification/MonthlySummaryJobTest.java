package com.fintrack.notification;

import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import com.fintrack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonthlySummaryJobTest {
    UserRepository users = mock(UserRepository.class);
    TransactionRepository tx = mock(TransactionRepository.class);
    NotificationService notifications = mock(NotificationService.class);
    MonthlySummaryJob job = new MonthlySummaryJob(users, tx, notifications);

    @Test
    void summaryMentionsIncomeExpensesAndSavings() {
        YearMonth m = YearMonth.of(2026, 10);
        when(tx.sumByType(eq(7L), eq(TransactionType.INCOME), any(), any())).thenReturn(new BigDecimal("10000.00"));
        when(tx.sumByType(eq(7L), eq(TransactionType.EXPENSE), any(), any())).thenReturn(new BigDecimal("3000.00"));
        job.summarize(7L, m);
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyOnce(eq(7L), eq(NotificationType.MONTHLY_SUMMARY), any(), msg.capture(), eq("SUMMARY:2026-10"));
        assertTrue(msg.getValue().contains("saved 7000.00"));
    }

    @Test
    void emptyMonthsProduceNoNotification() {
        when(tx.sumByType(eq(7L), any(), any(), any())).thenReturn(null);
        job.summarize(7L, YearMonth.of(2026, 10));
        verifyNoInteractions(notifications);
    }
}
