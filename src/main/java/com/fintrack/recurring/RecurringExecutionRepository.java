package com.fintrack.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringExecutionRepository extends JpaRepository<RecurringExecution, Long> {
    boolean existsByRecurringIdAndScheduledDate(Long recurringId, LocalDate scheduledDate);
    List<RecurringExecution> findByRecurringIdAndUserIdOrderByScheduledDateDesc(Long recurringId, Long userId);
}
