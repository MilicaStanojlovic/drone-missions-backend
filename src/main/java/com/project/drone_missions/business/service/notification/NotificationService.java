package com.project.drone_missions.business.service.notification;

import com.project.drone_missions.business.exception.notification.NotificationNotFoundException;
import com.project.drone_missions.data.model.Notification;
import com.project.drone_missions.data.model.NotificationType;
import com.project.drone_missions.data.repository.NotificationRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

    /** Create and persist a notification for a user. */
    public Notification create(NewNotification request) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(request.userId()));
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setMission(request.mission());
        return repository.save(notification);
    }

    /** The caller's notifications, newest first. */
    public List<Notification> listFor(Long userId) {
        return repository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /** How many of the caller's notifications are unread. */
    public long unreadCount(Long userId) {
        return repository.countByUser_IdAndReadAtIsNull(userId);
    }

    /** Mark one of the caller's notifications read (idempotent). */
    public void markRead(Long id, Long userId) {
        Notification notification = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            repository.save(notification);
        }
    }

    /** Mark all of the caller's unread notifications read. */
    public void markAllRead(Long userId) {
        Instant now = Instant.now();
        repository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .forEach(n -> {
                    n.setReadAt(now);
                    repository.save(n);
                });
    }

    /** Whether an overdue notification already exists for this pilot + mission (dedup). */
    public boolean overdueExists(Long userId, Long missionId) {
        return repository.existsByUser_IdAndMission_IdAndType(userId, missionId, NotificationType.MISSION_OVERDUE);
    }
}
