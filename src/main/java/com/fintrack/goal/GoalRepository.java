package com.fintrack.goal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<SavingsGoal, Long> {
    Optional<SavingsGoal> findByIdAndUserId(Long id, Long userId);

    List<SavingsGoal> findByUserIdOrderByStatusAscCreatedAtDesc(Long userId);

    /** Locks every goal of the user so concurrent contributions are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.userId = :userId order by g.id")
    List<SavingsGoal> lockAllByUserId(@Param("userId") Long userId);
}
