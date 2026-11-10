package com.fintrack.account;

import com.fintrack.account.AccountDtos.*;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        return accounts.findByUserIdOrderByCreatedAtDesc(userId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long userId, Long id) {
        return AccountResponse.from(accounts.findByIdAndUserId(id, userId).orElseThrow(this::notFound));
    }

    @Transactional
    public AccountResponse create(Long userId, CreateAccountRequest r) {
        String name = r.name().trim();
        if (accounts.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "You already have an account with this name");
        }
        BigDecimal initial = r.initialBalance().setScale(2, RoundingMode.HALF_UP);
        Account a = new Account();
        a.setUserId(userId);
        a.setName(name);
        a.setType(r.type());
        a.setCurrency(r.currency() == null ? "INR" : r.currency().toUpperCase());
        a.setInitialBalance(initial);
        a.setCurrentBalance(initial);
        return AccountResponse.from(accounts.save(a));
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, UpdateAccountRequest r) {
        Account a = lockOwned(userId, id);
        String name = r.name().trim();
        if (!name.equalsIgnoreCase(a.getName()) && accounts.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "You already have an account with this name");
        }
        a.setName(name);
        if (r.active() != null) a.setActive(r.active());
        return AccountResponse.from(a);
    }

    /** Accounts are never hard-deleted: history is preserved and the account is deactivated. */
    @Transactional
    public void deactivate(Long userId, Long id) {
        lockOwned(userId, id).setActive(false);
    }

    /** Net worth: money held in active asset accounts minus amounts owed on active credit cards. */
    @Transactional(readOnly = true)
    public BigDecimal netWorth(Long userId) {
        return accounts.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(Account::isActive)
                .map(a -> a.isCreditCard() ? a.getCurrentBalance().negate() : a.getCurrentBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Loads an account owned by the user with a write lock. Other users' accounts look like "not found". */
    @Transactional
    public Account lockOwned(Long userId, Long id) {
        return accounts.lockByIdAndUserId(id, userId).orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Account not found"); }
}
