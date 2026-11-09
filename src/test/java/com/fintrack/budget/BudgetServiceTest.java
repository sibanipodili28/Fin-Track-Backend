package com.fintrack.budget;

import com.fintrack.budget.BudgetDtos.*;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import com.fintrack.category.CategoryType;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetServiceTest {
    @Mock BudgetRepository budgets;
    @Mock CategoryRepository categories;
    @Mock TransactionRepository transactions;
    @InjectMocks BudgetService service;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void statusThresholds() {
        BigDecimal limit = bd("5000.00");
        assertEquals(BudgetStatus.ON_TRACK, BudgetService.statusOf(bd("3999.99"), limit));
        assertEquals(BudgetStatus.NEAR_LIMIT, BudgetService.statusOf(bd("4000.00"), limit));
        assertEquals(BudgetStatus.NEAR_LIMIT, BudgetService.statusOf(bd("4999.99"), limit));
        assertEquals(BudgetStatus.LIMIT_REACHED, BudgetService.statusOf(bd("5000.00"), limit));
        assertEquals(BudgetStatus.EXCEEDED, BudgetService.statusOf(bd("5000.01"), limit));
    }

    @Test
    void percentIsRoundedToOneDecimalAndSafeForZero() {
        assertEquals(bd("70.0"), BudgetService.percent(bd("3500"), bd("5000")));
        assertEquals(bd("0.0"), BudgetService.percent(bd("10"), BigDecimal.ZERO));
    }

    @Test
    void overviewUsesOnlyExpenseTotalsAndComputesRemaining() {
        Budget food = new Budget();
        food.setId(1L);
        food.setCategoryId(10L);
        food.setAmount(bd("5000.00"));
        food.setBudgetMonth(LocalDate.of(2026, 11, 1));
        Category cat = new Category();
        cat.setId(10L);
        cat.setName("Food");
        cat.setType(CategoryType.EXPENSE);
        when(budgets.findByUserIdAndBudgetMonthOrderByIdAsc(7L, LocalDate.of(2026, 11, 1))).thenReturn(List.of(food));
        when(transactions.totalsByCategoryId(eq(7L), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(Collections.singletonList(new Object[]{10L, bd("3500.00")}));
        when(categories.findAllById(any())).thenReturn(List.of(cat));

        BudgetOverview o = service.overview(7L, YearMonth.of(2026, 11));
        assertEquals(bd("1500.00"), o.totalRemaining());
        assertEquals(bd("70.0"), o.utilizationPercent());
        assertEquals(BudgetStatus.ON_TRACK, o.items().get(0).status());
        assertTrue(o.alerts().isEmpty());
    }

    @Test
    void duplicateBudgetForSameCategoryAndMonthIsRejected() {
        Category cat = new Category();
        cat.setId(10L);
        cat.setName("Food");
        cat.setType(CategoryType.EXPENSE);
        when(categories.findVisible(10L, 7L)).thenReturn(Optional.of(cat));
        when(budgets.existsByUserIdAndCategoryIdAndBudgetMonth(7L, 10L, LocalDate.of(2026, 11, 1))).thenReturn(true);
        assertThrows(BusinessRuleException.class,
                () -> service.create(7L, new CreateBudgetRequest(10L, "2026-11", bd("100.00"))));
        verify(budgets, never()).save(any());
    }

    @Test
    void incomeCategoriesCannotHaveBudgets() {
        Category salary = new Category();
        salary.setId(1L);
        salary.setName("Salary");
        salary.setType(CategoryType.INCOME);
        when(categories.findVisible(1L, 7L)).thenReturn(Optional.of(salary));
        assertThrows(BusinessRuleException.class,
                () -> service.create(7L, new CreateBudgetRequest(1L, "2026-11", bd("100.00"))));
    }
}
