package com.fintrack.report;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.account.AccountType;
import com.fintrack.budget.BudgetService;
import com.fintrack.category.CategoryRepository;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.goal.GoalService;
import com.fintrack.report.ReportService.ReportParams;
import com.fintrack.transaction.TransactionRecord;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import com.fintrack.transfer.Transfer;
import com.fintrack.transfer.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {
    @Mock TransactionRepository transactions;
    @Mock TransferRepository transfers;
    @Mock AccountRepository accounts;
    @Mock CategoryRepository categories;
    @Mock BudgetService budgetService;
    @Mock GoalService goalService;
    @InjectMocks ReportService service;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(0, bd(expected).compareTo((BigDecimal) actual), "expected " + expected + " but was " + actual);
    }

    private Account account(long id, AccountType type, String initial) {
        Account a = new Account();
        a.setId(id);
        a.setName("Acc" + id);
        a.setType(type);
        a.setCurrency("INR");
        a.setInitialBalance(bd(initial));
        a.setCurrentBalance(bd(initial));
        a.setActive(true);
        return a;
    }

    private TransactionRecord tx(long id, TransactionType type, String amount, LocalDate date) {
        TransactionRecord t = new TransactionRecord();
        t.setId(id);
        t.setAccountId(1L);
        t.setCategoryId(3L);
        t.setType(type);
        t.setAmount(bd(amount));
        t.setTxnDate(date);
        t.setDescription("Item" + id);
        return t;
    }

    private Transfer transfer(long id, long from, long to, String amount, LocalDateTime at) {
        Transfer t = new Transfer();
        t.setId(id);
        t.setFromAccountId(from);
        t.setToAccountId(to);
        t.setAmount(bd(amount));
        t.setCreatedAt(at);
        return t;
    }

    @BeforeEach
    void setUp() {
        when(transactions.totalsBeforeForAccount(any(), any(), any())).thenReturn(Collections.emptyList());
        when(categories.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void monthlyReportHasTotalsSharesAndSavingsRate() {
        YearMonth m = YearMonth.of(2026, 11);
        when(transactions.sumByType(eq(7L), eq(TransactionType.INCOME), any(), any())).thenReturn(bd("10000.00"));
        when(transactions.sumByType(eq(7L), eq(TransactionType.EXPENSE), any(), any())).thenReturn(bd("4000.00"));
        when(transactions.totalsByCategory(eq(7L), eq(TransactionType.INCOME), any(), any()))
                .thenReturn(Collections.singletonList(new Object[]{"Salary", bd("10000.00")}));
        when(transactions.totalsByCategory(eq(7L), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(List.of(new Object[]{"Rent", bd("3000.00")}, new Object[]{"Food", bd("1000.00")}));

        ReportData r = service.monthly(7L, m);
        assertEquals(3, r.rows().size());
        assertEquals("75.0%", r.rows().get(1).get(3));
        assertEquals("25.0%", r.rows().get(2).get(3));
        assertMoney("6000.00", r.summary().get(2).value());
        assertEquals("60.0%", r.summary().get(3).value());
    }

    @Test
    void monthlyReportForAnEmptyMonthIsAllZeros() {
        ReportData r = service.monthly(7L, YearMonth.of(2026, 1));
        assertTrue(r.rows().isEmpty());
        assertMoney("0", r.summary().get(0).value());
        assertEquals("0.0%", r.summary().get(3).value());
    }

    @Test
    void yearlyReportFillsAllTwelveMonths() {
        when(transactions.monthlyTotals(eq(7L), any(), any())).thenReturn(List.of(
                new Object[]{2026, 3, TransactionType.INCOME, bd("5000.00")},
                new Object[]{2026, 3, TransactionType.EXPENSE, bd("2000.00")}));
        ReportData r = service.yearly(7L, 2026);
        assertEquals(12, r.rows().size());
        assertEquals("March", r.rows().get(2).get(0));
        assertMoney("3000.00", r.rows().get(2).get(3));
        assertEquals("60.0%", r.rows().get(2).get(4));
        assertMoney("0", r.rows().get(0).get(1));
    }

    @Test
    void statementRunningBalanceIncludesTransfersAndOpeningBalance() {
        Account acc = account(1, AccountType.SAVINGS, "1000.00");
        Account other = account(2, AccountType.CASH_WALLET, "0.00");
        when(accounts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(acc));
        when(accounts.findAllById(any())).thenReturn(List.of(acc, other));
        when(transactions.totalsBeforeForAccount(7L, 1L, LocalDate.of(2026, 11, 1)))
                .thenReturn(Collections.singletonList(new Object[]{TransactionType.INCOME, bd("500.00")}));
        when(transactions.findByUserIdAndAccountIdAndTxnDateBetweenOrderByTxnDateAscIdAsc(eq(7L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(tx(1, TransactionType.EXPENSE, "200.00", LocalDate.of(2026, 11, 5))));
        when(transfers.findForStatement(eq(7L), eq(1L), any(), any(), any())).thenReturn(List.of(
                transfer(1, 1, 2, "100.00", LocalDateTime.of(2026, 11, 6, 10, 0)),
                transfer(2, 2, 1, "50.00", LocalDateTime.of(2026, 11, 7, 10, 0))));

        ReportData r = service.statement(7L, 1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertEquals(3, r.rows().size());
        assertMoney("1500.00", r.summary().get(0).value());
        assertMoney("1300.00", r.rows().get(0).get(4));
        assertMoney("1200.00", r.rows().get(1).get(4));
        assertMoney("1250.00", r.rows().get(2).get(4));
        assertMoney("1250.00", r.summary().get(3).value());
        assertMoney("50.00", r.summary().get(1).value());
        assertMoney("300.00", r.summary().get(2).value());
        assertEquals("Transfer to Acc2", r.rows().get(1).get(1));
        assertEquals("Transfer from Acc2", r.rows().get(2).get(1));
    }

    @Test
    void creditCardStatementTracksAmountOwed() {
        Account card = account(1, AccountType.CREDIT_CARD, "0.00");
        Account bank = account(2, AccountType.SAVINGS, "5000.00");
        when(accounts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(card));
        when(accounts.findAllById(any())).thenReturn(List.of(card, bank));
        when(transactions.findByUserIdAndAccountIdAndTxnDateBetweenOrderByTxnDateAscIdAsc(eq(7L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(tx(1, TransactionType.EXPENSE, "1000.00", LocalDate.of(2026, 11, 2))));
        when(transfers.findForStatement(eq(7L), eq(1L), any(), any(), any()))
                .thenReturn(List.of(transfer(1, 2, 1, "400.00", LocalDateTime.of(2026, 11, 3, 9, 0))));

        ReportData r = service.statement(7L, 1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertMoney("1000.00", r.rows().get(0).get(4));
        assertMoney("600.00", r.rows().get(1).get(4));
        assertTrue(r.subtitle().contains("amount owed"));
    }

    @Test
    void statementRejectsOtherUsersAccountsMissingIdsAndBadRanges() {
        when(accounts.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.statement(7L, 9L, null, null));
        assertThrows(BusinessRuleException.class, () -> service.statement(7L, null, null, null));
        when(accounts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, "0")));
        assertThrows(BusinessRuleException.class, () -> service.statement(7L, 1L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)));
        assertThrows(BusinessRuleException.class, () -> service.statement(7L, 1L, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void reportTypeAndFormatParsingIsStrict() {
        assertEquals(ReportType.ACCOUNT_STATEMENT, ReportType.parse("account-statement"));
        assertThrows(BusinessRuleException.class, () -> ReportType.parse("../etc/passwd"));
        assertEquals(ReportFormat.XLSX, ReportFormat.parse("xlsx"));
        assertThrows(BusinessRuleException.class, () -> ReportFormat.parse("docx"));
        assertNotNull(new ReportParams(null, null, null, null, null));
    }
}
