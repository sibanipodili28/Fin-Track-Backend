package com.fintrack.goal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "savings_goals")
@Getter @Setter @NoArgsConstructor
public class SavingsGoal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;
    @Column(name = "saved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO.setScale(2);
    @Column(name = "target_date")
    private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private GoalStatus status = GoalStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Version
    private Long version;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    public BigDecimal remaining() { return targetAmount.subtract(savedAmount); }
}
