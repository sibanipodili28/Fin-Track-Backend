package com.fintrack.admin;

import com.fintrack.account.AccountRepository;
import com.fintrack.category.Category;
import com.fintrack.category.CategoryController;
import com.fintrack.category.CategoryRepository;
import com.fintrack.category.CategoryType;
import com.fintrack.common.BusinessRuleException;
import com.fintrack.transaction.TransactionRepository;
import com.fintrack.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Admin-only endpoints (enforced in SecurityConfig: /api/v1/admin/** requires ROLE_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    public record Stats(long users, long accounts, long transactions) {}
    public record NewCategory(@NotBlank @Size(max = 80) String name, @NotNull CategoryType type) {}

    private final UserRepository users;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    @GetMapping("/stats")
    public Stats stats() { return new Stats(users.count(), accounts.count(), transactions.count()); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryController.CategoryResponse createSystemCategory(@Valid @RequestBody NewCategory r) {
        String name = r.name().trim();
        if (categories.existsByUserIdIsNullAndNameIgnoreCaseAndType(name, r.type())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Category already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setType(r.type());
        c.setSystemCategory(true);
        c = categories.save(c);
        return new CategoryController.CategoryResponse(c.getId(), c.getName(), c.getType());
    }
}
