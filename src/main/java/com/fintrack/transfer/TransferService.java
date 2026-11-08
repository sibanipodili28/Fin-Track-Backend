package com.fintrack.transfer;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.account.AccountService;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.transfer.TransferDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferService {
    private final TransferRepository transfers;
    private final AccountService accountService;
    private final AccountRepository accounts;

    /**
     * Moves money between two accounts of the same user in one database transaction.
     * Both rows are locked in id order to avoid deadlocks. A repeated Idempotency-Key returns the original transfer.
     * A transfer is not income or expense, so it never appears in budgets or spending totals.
     */
    @Transactional
    public TransferResponse create(Long userId, TransferRequest r, String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) throw new BusinessRuleException("Idempotency-Key is too long");
        if (key != null) {
            var existing = transfers.findByUserIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) return toResponses(List.of(existing.get())).get(0);
        }
        if (r.fromAccountId().equals(r.toAccountId())) {
            throw new BusinessRuleException("Source and destination accounts must be different");
        }
        var amount = r.amount().setScale(2, RoundingMode.HALF_UP);

        Account from;
        Account to;
        if (r.fromAccountId() < r.toAccountId()) {
            from = accountService.lockOwned(userId, r.fromAccountId());
            to = accountService.lockOwned(userId, r.toAccountId());
        } else {
            to = accountService.lockOwned(userId, r.toAccountId());
            from = accountService.lockOwned(userId, r.fromAccountId());
        }
        if (!from.isActive() || !to.isActive()) {
            throw new BusinessRuleException("Transfers need two active accounts");
        }
        if (from.isCreditCard()) {
            throw new BusinessRuleException("Transfers out of a credit card are not supported");
        }
        if (from.getCurrentBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in account '" + from.getName() + "'");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Transfers between different currencies are not supported");
        }

        from.apply(amount.negate());
        // Paying into a credit card reduces the amount owed.
        to.apply(to.isCreditCard() ? amount.negate() : amount);

        Transfer t = new Transfer();
        t.setUserId(userId);
        t.setFromAccountId(from.getId());
        t.setToAccountId(to.getId());
        t.setAmount(amount);
        t.setDescription(r.description() == null ? null : r.description().trim());
        t.setIdempotencyKey(key);
        return toResponses(List.of(transfers.save(t))).get(0);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> list(Long userId) {
        return toResponses(transfers.findTop100ByUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long userId, Long id) {
        return toResponses(List.of(transfers.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found")))).get(0);
    }

    private List<TransferResponse> toResponses(List<Transfer> rows) {
        Set<Long> ids = rows.stream().flatMap(t -> java.util.stream.Stream.of(t.getFromAccountId(), t.getToAccountId()))
                .collect(Collectors.toSet());
        Map<Long, String> names = accounts.findAllById(ids).stream().collect(Collectors.toMap(Account::getId, Account::getName));
        return rows.stream().map(t -> new TransferResponse(t.getId(), t.getFromAccountId(), names.get(t.getFromAccountId()),
                t.getToAccountId(), names.get(t.getToAccountId()), t.getAmount(), t.getDescription(), t.getCreatedAt())).toList();
    }
}
