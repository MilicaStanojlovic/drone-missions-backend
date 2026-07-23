package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Notification;
import com.project.drone_missions.data.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Guards the overdue scheduler so each mission is notified only once. */
    boolean existsByUserIdAndMissionIdAndType(Long userId, Long missionId, NotificationType type);
}
