package com.fintrack.transfer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    List<Transfer> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    @Query("select sum(t.amount) from Transfer t where t.userId = :uid and t.fromAccountId = :aid and t.createdAt < :before")
    BigDecimal sumOutBefore(@Param("uid") Long uid, @Param("aid") Long aid, @Param("before") LocalDateTime before);

    @Query("select sum(t.amount) from Transfer t where t.userId = :uid and t.toAccountId = :aid and t.createdAt < :before")
    BigDecimal sumInBefore(@Param("uid") Long uid, @Param("aid") Long aid, @Param("before") LocalDateTime before);

    @Query("select t from Transfer t where t.userId = :uid and (t.fromAccountId = :aid or t.toAccountId = :aid) "
            + "and t.createdAt >= :start and t.createdAt < :end order by t.createdAt, t.id")
    List<Transfer> findForStatement(@Param("uid") Long uid, @Param("aid") Long aid, @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end, Pageable pageable);

    Optional<Transfer> findByIdAndUserId(Long id, Long userId);
    Optional<Transfer> findByUserIdAndIdempotencyKey(Long userId, String key);
}
