package com.fintrack;

import com.fintrack.account.Account;
import com.fintrack.account.AccountType;
import com.fintrack.common.InsufficientBalanceException;
import com.fintrack.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountBalanceRulesTest {

    private Account account(AccountType type, String balance) {
        Account a = new Account();
        a.setName("Test");
        a.setType(type);
        a.setCurrentBalance(new BigDecimal(balance));
        return a;
    }

    @Test
    void assetAccountFollowsOpeningPlusIncomeMinusExpense() {
        Account a = account(AccountType.SAVINGS, "20000.00");
        a.apply(a.effectOf(TransactionType.INCOME, new BigDecimal("10000.00")));
        a.apply(a.effectOf(TransactionType.EXPENSE, new BigDecimal("3000.00")));
        assertEquals(new BigDecimal("27000.00"), a.getCurrentBalance());
    }

    @Test
    void creditCardBalanceIsAmountOwed() {
        Account card = account(AccountType.CREDIT_CARD, "0.00");
        card.apply(card.effectOf(TransactionType.EXPENSE, new BigDecimal("1500.00")));
        assertEquals(new BigDecimal("1500.00"), card.getCurrentBalance());
        card.apply(card.effectOf(TransactionType.INCOME, new BigDecimal("500.00")));
        assertEquals(new BigDecimal("1000.00"), card.getCurrentBalance());
        assertDoesNotThrow(card::assertNotOverdrawn);
    }

    @Test
    void assetAccountCannotGoNegative() {
        Account a = account(AccountType.CASH_WALLET, "100.00");
        a.apply(a.effectOf(TransactionType.EXPENSE, new BigDecimal("100.01")));
        assertThrows(InsufficientBalanceException.class, a::assertNotOverdrawn);
    }
}
