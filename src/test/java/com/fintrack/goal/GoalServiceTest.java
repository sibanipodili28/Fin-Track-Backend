package com.fintrack.goal;

import com.fintrack.account.AccountService;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.goal.GoalDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalServiceTest {
    @Mock GoalRepository goals;
    @Mock ContributionRepository contributions;
    @Mock AccountService accountService;
    @InjectMocks GoalService service;

    SavingsGoal laptop;
    SavingsGoal fund;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private SavingsGoal goal(long id, String target, String saved) {
        SavingsGoal g = new SavingsGoal();
        g.setId(id);
        g.setName("G" + id);
        g.setTargetAmount(bd(target));
        g.setSavedAmount(bd(saved));
        return g;
    }

    @BeforeEach
    void setUp() {
        laptop = goal(1, "1000.00", "0.00");
        fund = goal(2, "5000.00", "2000.00");
        when(goals.lockAllByUserId(7L)).thenReturn(List.of(laptop, fund));
        when(accountService.netWorth(7L)).thenReturn(bd("3000.00"));
    }

    private ContributionRequest req(String amount) { return new ContributionRequest(bd(amount), null, null); }

    @Test
    void contributionIncreasesSavedAmountAndDoesNotTouchAccounts() {
        GoalResponse r = service.contribute(7L, 1L, req("400.00"));
        assertEquals(bd("400.00"), r.savedAmount());
        assertEquals(bd("600.00"), r.remainingAmount());
        assertEquals(bd("40.0"), r.progressPercent());
        verify(contributions).save(any(SavingsContribution.class));
        verify(accountService, never()).lockOwned(any(), any());
    }

    @Test
    void reachingTheTargetCompletesTheGoal() {
        GoalResponse r = service.contribute(7L, 1L, req("1000.00"));
        assertEquals(GoalStatus.COMPLETED, r.status());
        assertNotNull(laptop.getCompletedAt());
    }

    @Test
    void cannotContributeMoreThanRemaining() {
        assertThrows(BusinessRuleException.class, () -> service.contribute(7L, 1L, req("1000.01")));
        assertEquals(bd("0.00"), laptop.getSavedAmount());
        verify(contributions, never()).save(any());
    }

    @Test
    void cannotSetAsideMoreThanUnallocatedBalance() {
        // net worth 3000, already earmarked 2000 in the other goal -> only 1000 is free
        laptop.setTargetAmount(bd("5000.00"));
        assertThrows(BusinessRuleException.class, () -> service.contribute(7L, 1L, req("1000.01")));
        assertDoesNotThrow(() -> service.contribute(7L, 1L, req("1000.00")));
    }

    @Test
    void completedGoalsRejectContributions() {
        laptop.setStatus(GoalStatus.COMPLETED);
        assertThrows(BusinessRuleException.class, () -> service.contribute(7L, 1L, req("10.00")));
    }

    @Test
    void unknownOrForeignGoalLooksLikeNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.contribute(7L, 99L, req("10.00")));
    }
}
