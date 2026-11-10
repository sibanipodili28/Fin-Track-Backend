package com.fintrack.recurring;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserIdOrderByStatusAscNextExecutionDateAsc(Long userId);
    Optional<RecurringTransaction> findByIdAndUserId(Long id, Long userId);
    List<RecurringTransaction> findByStatusAndNextExecutionDateLessThanEqual(RecurringStatus status, LocalDate date);
    List<RecurringTransaction> findByUserIdAndStatusAndNextExecutionDateLessThanEqual(Long userId, RecurringStatus status, LocalDate date);
    List<RecurringTransaction> findByStatusAndNextExecutionDateBetween(RecurringStatus status, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id")
    Optional<RecurringTransaction> lockById(@Param("id") Long id);
}
