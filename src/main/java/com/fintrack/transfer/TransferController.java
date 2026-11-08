package com.fintrack.transfer;

import com.fintrack.security.AuthUser;
import com.fintrack.transfer.TransferDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final TransferService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TransferRequest r,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.create(me.id(), r, key);
    }

    @GetMapping
    public List<TransferResponse> list(@AuthenticationPrincipal AuthUser me) { return service.list(me.id()); }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return service.get(me.id(), id); }
}
