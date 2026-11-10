package com.fintrack.recurring;

import com.fintrack.transaction.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "recurring_transactions")
@Getter @Setter @NoArgsConstructor
public class RecurringTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "category_id", nullable = false)
    private Long categoryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private TransactionType type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(length = 255)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Frequency frequency;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;
    @Column(name = "anchor_day", nullable = false)
    private int anchorDay;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private RecurringStatus status = RecurringStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    private Long version;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    /** Moves to the occurrence after {@code executed}; the rule ends when that would pass the end date. */
    public void advanceAfter(LocalDate executed) {
        LocalDate next = RecurringSchedule.nextAfter(executed, frequency, anchorDay);
        nextExecutionDate = next;
        if (endDate != null && next.isAfter(endDate)) status = RecurringStatus.ENDED;
    }

    /** Skips occurrences that were missed while paused, so resuming never back-posts old transactions. */
    public void skipTo(LocalDate today) {
        int guard = 0;
        while (nextExecutionDate.isBefore(today) && status == RecurringStatus.ACTIVE && guard++ < 4000) {
            advanceAfter(nextExecutionDate);
        }
    }
}
