package com.fintrack.recurring;

import com.fintrack.recurring.RecurringDtos.*;
import com.fintrack.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recurring")
@RequiredArgsConstructor
public class RecurringController {
    private final RecurringService service;
    private final RecurringRunner runner;

    @GetMapping
    public List<RecurringResponse> list(@AuthenticationPrincipal AuthUser me) { return service.list(me.id()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRecurringRequest r) { return service.create(me.id(), r); }

    @PutMapping("/{id}")
    public RecurringResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody UpdateRecurringRequest r) {
        return service.update(me.id(), id, r);
    }

    @PostMapping("/{id}/pause")
    public RecurringResponse pause(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.pause(me.id(), id); }

    @PostMapping("/{id}/resume")
    public RecurringResponse resume(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.resume(me.id(), id); }

    @PostMapping("/{id}/cancel")
    public RecurringResponse cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.cancel(me.id(), id); }

    @GetMapping("/{id}/history")
    public List<ExecutionResponse> history(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.history(me.id(), id); }

    /** Runs the caller's own due rules now. The scheduler does the same for everyone every night at 00:05. */
    @PostMapping("/run-due")
    public RunSummary runDue(@AuthenticationPrincipal AuthUser me) { return runner.runDue(me.id(), LocalDate.now()); }
}
