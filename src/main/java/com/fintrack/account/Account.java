package com.fintrack.account;

import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.transaction.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * For asset accounts currentBalance is money held. For CREDIT_CARD accounts it is the outstanding amount owed,
 * so an expense increases it and a payment/income decreases it.
 */
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AccountType type;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal initialBalance;
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    private Long version;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    public boolean isCreditCard() { return type == AccountType.CREDIT_CARD; }

    /** Signed change to currentBalance caused by a transaction of the given type. */
    public BigDecimal effectOf(TransactionType txType, BigDecimal amount) {
        boolean increases = (txType == TransactionType.INCOME) != isCreditCard();
        return increases ? amount : amount.negate();
    }

    public void apply(BigDecimal delta) { currentBalance = currentBalance.add(delta); }

    public void assertNotOverdrawn() {
        if (!isCreditCard() && currentBalance.signum() < 0) {
            throw new InsufficientBalanceException("Insufficient balance in account '" + name + "'");
        }
    }
}
