package com.fintrack.notification;

import java.time.LocalDateTime;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(Long id, NotificationType type, String title, String message, boolean read, LocalDateTime createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {}
}
