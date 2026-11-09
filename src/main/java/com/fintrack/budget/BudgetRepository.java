package com.fintrack.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
    List<Budget> findByUserIdAndBudgetMonthOrderByIdAsc(Long userId, LocalDate month);
    List<Budget> findByUserIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(Long userId, LocalDate from, LocalDate to);
    boolean existsByUserIdAndCategoryIdAndBudgetMonth(Long userId, Long categoryId, LocalDate month);
}
