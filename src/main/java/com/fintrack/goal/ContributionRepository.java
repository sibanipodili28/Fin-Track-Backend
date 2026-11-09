package com.fintrack.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContributionRepository extends JpaRepository<SavingsContribution, Long> {
    List<SavingsContribution> findByGoalIdAndUserIdOrderByContributionDateDescIdDesc(Long goalId, Long userId);
}
