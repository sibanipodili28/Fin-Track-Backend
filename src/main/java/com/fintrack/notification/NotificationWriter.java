package com.fintrack.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts in its own transaction so that a failed or duplicate notification can never roll back the
 * financial operation that triggered it.
 */
@Component
@RequiredArgsConstructor
public class NotificationWriter {
    private final NotificationRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Notification n) {
        repository.save(n);
    }
}
