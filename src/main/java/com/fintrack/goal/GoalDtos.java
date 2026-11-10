package com.fintrack.goal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class GoalDtos {
    private GoalDtos() {}

    public record GoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin(value = "0.01", message = "Target must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal targetAmount,
            LocalDate targetDate) {}

    public record ContributionRequest(
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
            @PastOrPresent(message = "Date cannot be in the future") LocalDate date,
            @Size(max = 255) String note) {}

    public record GoalResponse(Long id, String name, BigDecimal targetAmount, BigDecimal savedAmount, BigDecimal remainingAmount,
                               BigDecimal progressPercent, LocalDate targetDate, GoalStatus status, boolean overdue,
                               LocalDateTime createdAt, LocalDateTime completedAt) {
        public static GoalResponse from(SavingsGoal g) {
            BigDecimal progress = g.getTargetAmount().signum() == 0 ? BigDecimal.ZERO.setScale(1)
                    : g.getSavedAmount().multiply(BigDecimal.valueOf(100)).divide(g.getTargetAmount(), 1, java.math.RoundingMode.HALF_UP);
            boolean overdue = g.getStatus() == GoalStatus.ACTIVE && g.getTargetDate() != null && g.getTargetDate().isBefore(LocalDate.now());
            return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getSavedAmount(), g.remaining(), progress,
                    g.getTargetDate(), g.getStatus(), overdue, g.getCreatedAt(), g.getCompletedAt());
        }
    }

    public record ContributionResponse(Long id, BigDecimal amount, LocalDate date, String note, LocalDateTime createdAt) {
        public static ContributionResponse from(SavingsContribution c) {
            return new ContributionResponse(c.getId(), c.getAmount(), c.getContributionDate(), c.getNote(), c.getCreatedAt());
        }
    }
}
