package com.fintrack.notification;

import com.fintrack.common.PageResponse;
import com.fintrack.notification.NotificationDtos.NotificationResponse;
import com.fintrack.notification.NotificationDtos.UnreadCount;
import com.fintrack.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthUser me,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(me.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal AuthUser me) { return new UnreadCount(service.unreadCount(me.id())); }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.markRead(me.id(), id); }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser me) { service.markAllRead(me.id()); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { service.delete(me.id(), id); }
}
