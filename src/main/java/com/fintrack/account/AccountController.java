package com.fintrack.account;

import com.fintrack.account.AccountDtos.*;
import com.fintrack.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthUser me) { return service.list(me.id()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateAccountRequest r) {
        return service.create(me.id(), r);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.get(me.id(), id); }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                  @Valid @RequestBody UpdateAccountRequest r) {
        return service.update(me.id(), id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.deactivate(me.id(), id); }
}
