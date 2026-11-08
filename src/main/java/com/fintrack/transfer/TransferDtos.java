package com.fintrack.transfer;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TransferDtos {
    private TransferDtos() {}

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
            @Size(max = 255) String description) {}

    public record TransferResponse(Long id, Long fromAccountId, String fromAccountName, Long toAccountId,
                                   String toAccountName, BigDecimal amount, String description, LocalDateTime createdAt) {}
}
