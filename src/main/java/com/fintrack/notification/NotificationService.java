package com.fintrack.notification;

import com.fintrack.common.PageResponse;
import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.notification.NotificationDtos.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final NotificationWriter writer;

    /**
     * Creates an in-app notification. When a dedupeKey is given, the same key is only ever notified once per user
     * (for example one alert per budget and status), so repeated checks do not spam the user.
     */
    public void notifyOnce(Long userId, NotificationType type, String title, String message, String dedupeKey) {
        if (dedupeKey != null && repository.existsByUserIdAndDedupeKey(userId, dedupeKey)) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        n.setDedupeKey(dedupeKey);
        try {
            writer.insert(n);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate notification ignored: {}", dedupeKey);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Notification> result = unreadOnly ? repository.findByUserIdAndReadFalse(userId, pr) : repository.findByUserId(userId, pr);
        return PageResponse.of(result, result.getContent().stream().map(NotificationResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) { return repository.countByUserIdAndReadFalse(userId); }

    @Transactional
    public void markRead(Long userId, Long id) { find(userId, id).setRead(true); }

    @Transactional
    public int markAllRead(Long userId) { return repository.markAllRead(userId); }

    @Transactional
    public void delete(Long userId, Long id) { repository.delete(find(userId, id)); }

    private Notification find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    }
}
