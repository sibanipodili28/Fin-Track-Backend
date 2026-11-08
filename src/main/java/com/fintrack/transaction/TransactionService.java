package com.fintrack.transaction;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.account.AccountService;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryRepository;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.PageResponse;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.transaction.TransactionDtos.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private static final Set<String> SORTABLE = Set.of("txnDate", "amount", "createdAt");

    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest r) {
        Account acc = accountService.lockOwned(userId, r.accountId());
        if (!acc.isActive()) {
            throw new BusinessRuleException("Cannot add transactions to an inactive account");
        }
        Category cat = validCategory(userId, r);
        var amount = r.amount().setScale(2, RoundingMode.HALF_UP);

        acc.apply(acc.effectOf(r.type(), amount));
        acc.assertNotOverdrawn();

        TransactionRecord t = new TransactionRecord();
        t.setUserId(userId);
        fill(t, r, amount);
        return toResponses(List.of(transactions.save(t))).get(0);
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest r) {
        TransactionRecord t = find(userId, id);
        validCategory(userId, r);
        var amount = r.amount().setScale(2, RoundingMode.HALF_UP);

        // Lock every affected account in id order so concurrent edits cannot deadlock.
        Map<Long, Account> locked = new TreeMap<>();
        for (Long accountId : new TreeSet<>(List.of(t.getAccountId(), r.accountId()))) {
            locked.put(accountId, accountService.lockOwned(userId, accountId));
        }
        Account oldAcc = locked.get(t.getAccountId());
        Account newAcc = locked.get(r.accountId());

        boolean balanceAffecting = !oldAcc.getId().equals(newAcc.getId())
                || t.getType() != r.type() || t.getAmount().compareTo(amount) != 0;
        if (!newAcc.isActive() && balanceAffecting) {
            throw new BusinessRuleException("Balance-changing edits are not allowed on inactive accounts");
        }

        oldAcc.apply(oldAcc.effectOf(t.getType(), t.getAmount()).negate());
        newAcc.apply(newAcc.effectOf(r.type(), amount));
        oldAcc.assertNotOverdrawn();
        newAcc.assertNotOverdrawn();

        fill(t, r, amount);
        return toResponses(List.of(t)).get(0);
    }

    /** Deleting reverses the balance effect. Allowed on inactive accounts (historical clean-up). */
    @Transactional
    public void delete(Long userId, Long id) {
        TransactionRecord t = find(userId, id);
        Account acc = accountService.lockOwned(userId, t.getAccountId());
        acc.apply(acc.effectOf(t.getType(), t.getAmount()).negate());
        acc.assertNotOverdrawn();
        transactions.delete(t);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return toResponses(List.of(find(userId, id))).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long userId, TransactionFilter f, int page, int size,
                                                    String sortBy, String direction) {
        String sortProp = SORTABLE.contains(sortBy) ? sortBy : "txnDate";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortProp).and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        Specification<TransactionRecord> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (f.type() != null) p.add(cb.equal(root.get("type"), f.type()));
            if (f.categoryId() != null) p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            if (f.accountId() != null) p.add(cb.equal(root.get("accountId"), f.accountId()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("txnDate"), f.from()));
            if (f.to() != null) p.add(cb.lessThanOrEqualTo(root.get("txnDate"), f.to()));
            if (f.minAmount() != null) p.add(cb.greaterThanOrEqualTo(root.get("amount"), f.minAmount()));
            if (f.maxAmount() != null) p.add(cb.lessThanOrEqualTo(root.get("amount"), f.maxAmount()));
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("description")), like), cb.like(cb.lower(root.get("notes")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<TransactionRecord> result = transactions.findAll(spec, pr);
        return PageResponse.of(result, toResponses(result.getContent()));
    }

    /** Resolves account and category names with two bulk lookups instead of one per row. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> toResponses(List<TransactionRecord> rows) {
        Map<Long, String> accountNames = accounts.findAllById(rows.stream().map(TransactionRecord::getAccountId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Account::getId, Account::getName));
        Map<Long, String> categoryNames = categories.findAllById(rows.stream().map(TransactionRecord::getCategoryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        return rows.stream().map(t -> new TransactionResponse(t.getId(), t.getAccountId(), accountNames.get(t.getAccountId()),
                t.getCategoryId(), categoryNames.get(t.getCategoryId()), t.getType(), t.getAmount(), t.getDescription(),
                t.getNotes(), t.getTxnDate(), t.getCreatedAt())).toList();
    }

    private TransactionRecord find(Long userId, Long id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private Category validCategory(Long userId, TransactionRequest r) {
        Category cat = categories.findVisible(r.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (!cat.getType().name().equals(r.type().name())) {
            throw new BusinessRuleException("Category '" + cat.getName() + "' cannot be used for " + r.type().name().toLowerCase());
        }
        return cat;
    }

    private void fill(TransactionRecord t, TransactionRequest r, java.math.BigDecimal amount) {
        t.setAccountId(r.accountId());
        t.setCategoryId(r.categoryId());
        t.setType(r.type());
        t.setAmount(amount);
        t.setTxnDate(r.date());
        t.setDescription(r.description() == null ? null : r.description().trim());
        t.setNotes(r.notes() == null ? null : r.notes().trim());
    }
}
