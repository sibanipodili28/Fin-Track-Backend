package com.fintrack.transaction;

import com.fintrack.common.PageResponse;
import com.fintrack.security.AuthUser;
import com.fintrack.transaction.TransactionDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService service;

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "txnDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(me.id(), new TransactionFilter(type, categoryId, accountId, from, to, minAmount, maxAmount, search),
                page, size, sortBy, direction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TransactionRequest r) {
        return service.create(me.id(), r);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.get(me.id(), id); }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest r) {
        return service.update(me.id(), id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.delete(me.id(), id); }
}
