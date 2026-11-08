package com.fintrack.account;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AccountDtos {
    private AccountDtos() {}

    public record CreateAccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal initialBalance,
            @Pattern(regexp = "[A-Za-z]{3}", message = "Currency must be a 3-letter code") String currency) {}

    public record UpdateAccountRequest(@NotBlank @Size(max = 100) String name, Boolean active) {}

    public record AccountResponse(Long id, String name, AccountType type, String currency, BigDecimal initialBalance,
                                  BigDecimal currentBalance, boolean active, LocalDateTime createdAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getCurrency(), a.getInitialBalance(),
                    a.getCurrentBalance(), a.isActive(), a.getCreatedAt());
        }
    }
}
