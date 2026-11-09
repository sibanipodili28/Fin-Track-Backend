package com.fintrack.goal;

import com.fintrack.account.AccountService;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.goal.GoalDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A contribution earmarks money you already have. It never changes any account balance.
 * The total earmarked across active goals may not exceed your net worth.
 */
@Service
@RequiredArgsConstructor
public class GoalService {
    private final GoalRepository goals;
    private final ContributionRepository contributions;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        return goals.findByUserIdOrderByStatusAscCreatedAtDesc(userId).stream().map(GoalResponse::from).toList();
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest r) {
        if (r.targetDate() != null && r.targetDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Target date cannot be in the past");
        }
        SavingsGoal g = new SavingsGoal();
        g.setUserId(userId);
        g.setName(r.name().trim());
        g.setTargetAmount(r.targetAmount().setScale(2, RoundingMode.HALF_UP));
        g.setTargetDate(r.targetDate());
        return GoalResponse.from(goals.save(g));
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest r) {
        SavingsGoal g = find(userId, id);
        requireActive(g);
        BigDecimal target = r.targetAmount().setScale(2, RoundingMode.HALF_UP);
        if (target.compareTo(g.getSavedAmount()) <= 0) {
            throw new BusinessRuleException("Target must be greater than the " + g.getSavedAmount() + " already saved. Complete the goal instead.");
        }
        g.setName(r.name().trim());
        g.setTargetAmount(target);
        g.setTargetDate(r.targetDate());
        return GoalResponse.from(g);
    }

    @Transactional
    public GoalResponse contribute(Long userId, Long goalId, ContributionRequest r) {
        List<SavingsGoal> all = goals.lockAllByUserId(userId);
        SavingsGoal g = all.stream().filter(x -> x.getId().equals(goalId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        requireActive(g);

        BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(g.remaining()) > 0) {
            throw new BusinessRuleException("Contribution is more than the " + g.remaining() + " still needed for this goal");
        }
        BigDecimal earmarked = all.stream().filter(x -> x.getStatus() == GoalStatus.ACTIVE)
                .map(SavingsGoal::getSavedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unallocated = accountService.netWorth(userId).subtract(earmarked).max(BigDecimal.ZERO);
        if (amount.compareTo(unallocated) > 0) {
            throw new BusinessRuleException("Only " + unallocated + " of your balance is not already set aside for goals");
        }

        SavingsContribution c = new SavingsContribution();
        c.setGoalId(g.getId());
        c.setUserId(userId);
        c.setAmount(amount);
        c.setContributionDate(r.date() == null ? LocalDate.now() : r.date());
        c.setNote(r.note() == null ? null : r.note().trim());
        contributions.save(c);

        g.setSavedAmount(g.getSavedAmount().add(amount));
        if (g.getSavedAmount().compareTo(g.getTargetAmount()) >= 0) markCompleted(g);
        return GoalResponse.from(g);
    }

    @Transactional
    public GoalResponse complete(Long userId, Long id) {
        SavingsGoal g = find(userId, id);
        requireActive(g);
        markCompleted(g);
        return GoalResponse.from(g);
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> history(Long userId, Long id) {
        find(userId, id);
        return contributions.findByGoalIdAndUserIdOrderByContributionDateDescIdDesc(id, userId).stream()
                .map(ContributionResponse::from).toList();
    }

    private void markCompleted(SavingsGoal g) {
        g.setStatus(GoalStatus.COMPLETED);
        g.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    private void requireActive(SavingsGoal g) {
        if (g.getStatus() != GoalStatus.ACTIVE) throw new BusinessRuleException("This goal is already completed");
    }

    private SavingsGoal find(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    }
}
