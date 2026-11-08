package com.fintrack.transaction;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TransactionDtos {
    private TransactionDtos() {}

    public record TransactionRequest(
            @NotNull Long accountId,
            @NotNull Long categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 255) String description,
            @Size(max = 1000) String notes) {}

    public record TransactionResponse(Long id, Long accountId, String accountName, Long categoryId, String categoryName,
                                      TransactionType type, BigDecimal amount, String description, String notes,
                                      LocalDate date, LocalDateTime createdAt) {}

    public record TransactionFilter(TransactionType type, Long categoryId, Long accountId, LocalDate from, LocalDate to,
                                    BigDecimal minAmount, BigDecimal maxAmount, String search) {}
}
