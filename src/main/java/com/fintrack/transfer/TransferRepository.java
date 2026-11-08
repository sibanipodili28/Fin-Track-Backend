package com.fintrack.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    List<Transfer> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Optional<Transfer> findByIdAndUserId(Long id, Long userId);
    Optional<Transfer> findByUserIdAndIdempotencyKey(Long userId, String key);
}
