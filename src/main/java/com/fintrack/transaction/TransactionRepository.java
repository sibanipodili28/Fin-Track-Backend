package com.fintrack.transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long>, JpaSpecificationExecutor<TransactionRecord> {
    Optional<TransactionRecord> findByIdAndUserId(Long id, Long userId);

    List<TransactionRecord> findTop5ByUserIdOrderByTxnDateDescIdDesc(Long userId);

    @Query("select sum(t.amount) from TransactionRecord t where t.userId = :uid and t.type = :type "
            + "and t.txnDate between :from and :to")
    BigDecimal sumByType(@Param("uid") Long uid, @Param("type") TransactionType type,
                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select c.name, sum(t.amount) from TransactionRecord t join Category c on c.id = t.categoryId "
            + "where t.userId = :uid and t.type = :type and t.txnDate between :from and :to "
            + "group by c.name order by sum(t.amount) desc")
    List<Object[]> totalsByCategory(@Param("uid") Long uid, @Param("type") TransactionType type,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select year(t.txnDate), month(t.txnDate), t.type, sum(t.amount) from TransactionRecord t "
            + "where t.userId = :uid and t.txnDate between :from and :to "
            + "group by year(t.txnDate), month(t.txnDate), t.type")
    List<Object[]> monthlyTotals(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t.categoryId, sum(t.amount) from TransactionRecord t "
            + "where t.userId = :uid and t.type = :type and t.txnDate between :from and :to group by t.categoryId")
    List<Object[]> totalsByCategoryId(@Param("uid") Long uid, @Param("type") TransactionType type,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select year(t.txnDate), month(t.txnDate), t.categoryId, sum(t.amount) from TransactionRecord t "
            + "where t.userId = :uid and t.type = :type and t.txnDate between :from and :to "
            + "group by year(t.txnDate), month(t.txnDate), t.categoryId")
    List<Object[]> monthlyTotalsByCategory(@Param("uid") Long uid, @Param("type") TransactionType type,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t.type, sum(t.amount) from TransactionRecord t where t.userId = :uid and t.accountId = :aid "
            + "and t.txnDate < :before group by t.type")
    List<Object[]> totalsBeforeForAccount(@Param("uid") Long uid, @Param("aid") Long aid, @Param("before") LocalDate before);

    List<TransactionRecord> findByUserIdAndAccountIdAndTxnDateBetweenOrderByTxnDateAscIdAsc(
            Long userId, Long accountId, LocalDate from, LocalDate to, Pageable pageable);
    long countByUserId(Long userId);
}
