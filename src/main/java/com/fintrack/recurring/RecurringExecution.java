package com.fintrack.recurring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "recurring_execution_history", uniqueConstraints = @UniqueConstraint(columnNames = {"recurring_id", "scheduled_date"}))
@Getter @Setter @NoArgsConstructor
public class RecurringExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recurring_id", nullable = false)
    private Long recurringId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private ExecutionStatus status;
    @Column(name = "transaction_id")
    private Long transactionId;
    @Column(length = 255)
    private String message;
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() { executedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
