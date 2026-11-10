package com.fintrack.recurring;

import com.fintrack.transaction.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class RecurringDtos {
    private RecurringDtos() {}

    public record CreateRecurringRequest(
            @NotNull Long accountId,
            @NotNull Long categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
            @Size(max = 255) String description,
            @NotNull Frequency frequency,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}

    public record UpdateRecurringRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
            @Size(max = 255) String description,
            LocalDate endDate) {}

    public record RecurringResponse(Long id, Long accountId, String accountName, Long categoryId, String categoryName,
                                    TransactionType type, BigDecimal amount, String description, Frequency frequency,
                                    LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate,
                                    RecurringStatus status, LocalDateTime createdAt) {}

    public record ExecutionResponse(Long id, LocalDate scheduledDate, ExecutionStatus status, Long transactionId,
                                    String message, LocalDateTime executedAt) {
        public static ExecutionResponse from(RecurringExecution e) {
            return new ExecutionResponse(e.getId(), e.getScheduledDate(), e.getStatus(), e.getTransactionId(), e.getMessage(), e.getExecutedAt());
        }
    }

    public record RunSummary(int executed, int failed) {}
}
