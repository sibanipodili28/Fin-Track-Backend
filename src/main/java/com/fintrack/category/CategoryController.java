package com.fintrack.category;

import com.fintrack.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    public record CategoryResponse(Long id, String name, CategoryType type) {}

    private final CategoryRepository categories;

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam(required = false) CategoryType type) {
        return categories.findAllVisible(me.id()).stream()
                .filter(c -> type == null || c.getType() == type)
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getType())).toList();
    }
}
