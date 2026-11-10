package com.fintrack.recurring;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.recurring.RecurringDtos.*;
import com.fintrack.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurringService {
    private final RecurringRepository rules;
    private final RecurringExecutionRepository history;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(Long userId) {
        return toResponses(rules.findByUserIdOrderByStatusAscNextExecutionDateAsc(userId));
    }

    @Transactional
    public RecurringResponse create(Long userId, CreateRecurringRequest r) {
        Account account = accounts.findByIdAndUserId(r.accountId(), userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.isActive()) throw new BusinessRuleException("The account is inactive");
        checkCategory(userId, r.categoryId(), r.type());
        if (r.startDate().isBefore(LocalDate.now())) throw new BusinessRuleException("Start date cannot be in the past");
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) throw new BusinessRuleException("End date must be on or after the start date");

        RecurringTransaction t = new RecurringTransaction();
        t.setUserId(userId);
        t.setAccountId(account.getId());
        t.setCategoryId(r.categoryId());
        t.setType(r.type());
        t.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        t.setDescription(r.description() == null ? null : r.description().trim());
        t.setFrequency(r.frequency());
        t.setStartDate(r.startDate());
        t.setEndDate(r.endDate());
        t.setNextExecutionDate(r.startDate());
        t.setAnchorDay(r.startDate().getDayOfMonth());
        return toResponses(List.of(rules.save(t))).get(0);
    }

    @Transactional
    public RecurringResponse update(Long userId, Long id, UpdateRecurringRequest r) {
        RecurringTransaction t = find(userId, id);
        if (t.getStatus() != RecurringStatus.ACTIVE && t.getStatus() != RecurringStatus.PAUSED) {
            throw new BusinessRuleException("Only active or paused rules can be edited");
        }
        checkCategory(userId, r.categoryId(), t.getType());
        if (r.endDate() != null && r.endDate().isBefore(t.getNextExecutionDate())) {
            throw new BusinessRuleException("End date must be on or after the next run date (" + t.getNextExecutionDate() + ")");
        }
        t.setCategoryId(r.categoryId());
        t.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        t.setDescription(r.description() == null ? null : r.description().trim());
        t.setEndDate(r.endDate());
        return toResponses(List.of(t)).get(0);
    }

    @Transactional
    public RecurringResponse pause(Long userId, Long id) {
        RecurringTransaction t = lock(userId, id);
        if (t.getStatus() != RecurringStatus.ACTIVE) throw new BusinessRuleException("Only active rules can be paused");
        t.setStatus(RecurringStatus.PAUSED);
        return toResponses(List.of(t)).get(0);
    }

    @Transactional
    public RecurringResponse resume(Long userId, Long id) {
        RecurringTransaction t = lock(userId, id);
        if (t.getStatus() != RecurringStatus.PAUSED) throw new BusinessRuleException("Only paused rules can be resumed");
        t.setStatus(RecurringStatus.ACTIVE);
        t.skipTo(LocalDate.now());
        return toResponses(List.of(t)).get(0);
    }

    @Transactional
    public RecurringResponse cancel(Long userId, Long id) {
        RecurringTransaction t = lock(userId, id);
        if (t.getStatus() == RecurringStatus.CANCELLED || t.getStatus() == RecurringStatus.ENDED) {
            throw new BusinessRuleException("This rule is already finished");
        }
        t.setStatus(RecurringStatus.CANCELLED);
        return toResponses(List.of(t)).get(0);
    }

    @Transactional(readOnly = true)
    public List<ExecutionResponse> history(Long userId, Long id) {
        find(userId, id);
        return history.findByRecurringIdAndUserIdOrderByScheduledDateDesc(id, userId).stream().map(ExecutionResponse::from).toList();
    }

    private void checkCategory(Long userId, Long categoryId, TransactionType type) {
        Category c = categories.findVisible(categoryId, userId).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (!c.getType().name().equals(type.name())) {
            throw new BusinessRuleException("Category '" + c.getName() + "' cannot be used for " + type.name().toLowerCase());
        }
    }

    private RecurringTransaction find(Long userId, Long id) {
        return rules.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));
    }

    private RecurringTransaction lock(Long userId, Long id) {
        find(userId, id);
        return rules.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));
    }

    private List<RecurringResponse> toResponses(List<RecurringTransaction> list) {
        Map<Long, String> accountNames = accounts.findAllById(list.stream().map(RecurringTransaction::getAccountId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Account::getId, Account::getName));
        Map<Long, String> categoryNames = categories.findAllById(list.stream().map(RecurringTransaction::getCategoryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        return list.stream().map(t -> new RecurringResponse(t.getId(), t.getAccountId(), accountNames.get(t.getAccountId()),
                t.getCategoryId(), categoryNames.get(t.getCategoryId()), t.getType(), t.getAmount(), t.getDescription(),
                t.getFrequency(), t.getStartDate(), t.getEndDate(), t.getNextExecutionDate(), t.getStatus(), t.getCreatedAt())).toList();
    }
}
