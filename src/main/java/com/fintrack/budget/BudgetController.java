package com.fintrack.budget;

import com.fintrack.budget.BudgetDtos.*;
import com.fintrack.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService service;

    @GetMapping
    public BudgetOverview overview(@AuthenticationPrincipal AuthUser me,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.overview(me.id(), month == null ? YearMonth.now() : month);
    }

    @GetMapping("/history")
    public List<BudgetHistoryPoint> history(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "6") int months) {
        return service.history(me.id(), months);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetItem create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateBudgetRequest r) {
        return service.create(me.id(), r);
    }

    @PutMapping("/{id}")
    public BudgetItem update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody UpdateBudgetRequest r) {
        return service.update(me.id(), id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.delete(me.id(), id); }
}
