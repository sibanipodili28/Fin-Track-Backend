package com.fintrack;

import com.fintrack.account.Account;
import com.fintrack.account.AccountRepository;
import com.fintrack.account.AccountService;
import com.fintrack.account.AccountType;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.transfer.Transfer;
import com.fintrack.transfer.TransferDtos.TransferRequest;
import com.fintrack.transfer.TransferRepository;
import com.fintrack.transfer.TransferService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {
    @Mock TransferRepository transfers;
    @Mock AccountService accountService;
    @Mock AccountRepository accounts;
    @InjectMocks TransferService service;

    Account a;
    Account b;

    private Account acc(long id, AccountType type, String balance) {
        Account x = new Account();
        x.setId(id);
        x.setName("A" + id);
        x.setType(type);
        x.setCurrency("INR");
        x.setActive(true);
        x.setCurrentBalance(new BigDecimal(balance));
        return x;
    }

    @BeforeEach
    void setUp() {
        a = acc(1, AccountType.SAVINGS, "1000.00");
        b = acc(2, AccountType.CASH_WALLET, "50.00");
        when(accountService.lockOwned(7L, 1L)).thenReturn(a);
        when(accountService.lockOwned(7L, 2L)).thenReturn(b);
        when(transfers.save(any(Transfer.class))).thenAnswer(i -> {
            Transfer t = i.getArgument(0);
            t.setId(99L);
            return t;
        });
        when(accounts.findAllById(any())).thenReturn(List.of(a, b));
    }

    @Test
    void transferMovesMoneyAndKeepsNetWorthUnchanged() {
        BigDecimal before = a.getCurrentBalance().add(b.getCurrentBalance());
        service.create(7L, new TransferRequest(1L, 2L, new BigDecimal("250.00"), "rent share"), null);
        assertEquals(new BigDecimal("750.00"), a.getCurrentBalance());
        assertEquals(new BigDecimal("300.00"), b.getCurrentBalance());
        assertEquals(before, a.getCurrentBalance().add(b.getCurrentBalance()));
    }

    @Test
    void insufficientBalanceIsRejectedWithoutChangingBalances() {
        assertThrows(InsufficientBalanceException.class,
                () -> service.create(7L, new TransferRequest(1L, 2L, new BigDecimal("1000.01"), null), null));
        assertEquals(new BigDecimal("1000.00"), a.getCurrentBalance());
        verify(transfers, never()).save(any());
    }

    @Test
    void sameAccountIsRejected() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(7L, new TransferRequest(1L, 1L, BigDecimal.TEN, null), null));
    }

    @Test
    void payingACreditCardReducesAmountOwed() {
        Account card = acc(2, AccountType.CREDIT_CARD, "400.00");
        when(accountService.lockOwned(7L, 2L)).thenReturn(card);
        service.create(7L, new TransferRequest(1L, 2L, new BigDecimal("150.00"), null), null);
        assertEquals(new BigDecimal("250.00"), card.getCurrentBalance());
        assertEquals(new BigDecimal("850.00"), a.getCurrentBalance());
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalWithoutMovingMoneyAgain() {
        Transfer existing = new Transfer();
        existing.setId(5L);
        existing.setFromAccountId(1L);
        existing.setToAccountId(2L);
        existing.setAmount(new BigDecimal("10.00"));
        when(transfers.findByUserIdAndIdempotencyKey(7L, "k1")).thenReturn(Optional.of(existing));
        var res = service.create(7L, new TransferRequest(1L, 2L, new BigDecimal("10.00"), null), "k1");
        assertEquals(5L, res.id());
        assertEquals(new BigDecimal("1000.00"), a.getCurrentBalance());
    }
}
