package com.fintrack.goal;

import com.fintrack.goal.GoalDtos.*;
import com.fintrack.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {
    private final GoalService service;

    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal AuthUser me) { return service.list(me.id()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GoalRequest r) { return service.create(me.id(), r); }

    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody GoalRequest r) {
        return service.update(me.id(), id, r);
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse contribute(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ContributionRequest r) {
        return service.contribute(me.id(), id, r);
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> history(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.history(me.id(), id); }

    @PostMapping("/{id}/complete")
    public GoalResponse complete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.complete(me.id(), id); }
}
