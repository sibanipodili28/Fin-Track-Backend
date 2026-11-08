package com.fintrack.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("select c from Category c where c.id = :id and (c.userId is null or c.userId = :userId)")
    Optional<Category> findVisible(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select c from Category c where c.userId is null or c.userId = :userId order by c.type, c.name")
    List<Category> findAllVisible(@Param("userId") Long userId);

    boolean existsByUserIdIsNullAndNameIgnoreCaseAndType(String name, CategoryType type);
}
