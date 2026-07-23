package com.project.drone_missions.business.service.notification;

import com.project.drone_missions.business.exception.notification.NotificationNotFoundException;
import com.project.drone_missions.data.model.Notification;
import com.project.drone_missions.data.model.NotificationType;
import com.project.drone_missions.data.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    /** Create and persist a notification for a user. */
    public Notification create(Long userId, NotificationType type, String title, String message, Long missionId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setMissionId(missionId);
        return repository.save(notification);
    }

    /** The caller's notifications, newest first. */
    public List<Notification> listFor(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** How many of the caller's notifications are unread. */
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    /** Mark one of the caller's notifications read (idempotent). */
    public void markRead(Long id, Long userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            repository.save(notification);
        }
    }

    /** Mark all of the caller's unread notifications read. */
    public void markAllRead(Long userId) {
        Instant now = Instant.now();
        repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .forEach(n -> {
                    n.setReadAt(now);
                    repository.save(n);
                });
    }

    /** Whether an overdue notification already exists for this pilot + mission (dedup). */
    public boolean overdueExists(Long userId, Long missionId) {
        return repository.existsByUserIdAndMissionIdAndType(userId, missionId, NotificationType.MISSION_OVERDUE);
    }
}
