package com.fintrack.category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(nullable = false, length = 80)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private CategoryType type;
    @Column(name = "is_system", nullable = false)
    private boolean systemCategory;
}
