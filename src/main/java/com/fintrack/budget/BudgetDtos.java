package com.fintrack.budget;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class BudgetDtos {
    private BudgetDtos() {}

    public enum BudgetStatus { ON_TRACK, NEAR_LIMIT, LIMIT_REACHED, EXCEEDED }

    public record CreateBudgetRequest(
            @NotNull Long categoryId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Month must look like 2026-11") String month,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount) {}

    public record UpdateBudgetRequest(
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount) {}

    public record BudgetItem(Long id, Long categoryId, String categoryName, BigDecimal amount, BigDecimal spent,
                             BigDecimal remaining, BigDecimal utilizationPercent, BudgetStatus status) {}

    public record BudgetOverview(String month, BigDecimal totalAllocated, BigDecimal totalSpent, BigDecimal totalRemaining,
                                 BigDecimal utilizationPercent, List<BudgetItem> items, List<String> alerts) {}

    public record BudgetHistoryPoint(String month, BigDecimal allocated, BigDecimal spent) {}
}
