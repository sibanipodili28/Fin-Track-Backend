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
@Table(name = "savings_contributions")
@Getter @Setter @NoArgsConstructor
public class SavingsContribution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "goal_id", nullable = false)
    private Long goalId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;
    @Column(length = 255)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }
}
