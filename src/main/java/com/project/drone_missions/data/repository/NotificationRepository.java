package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Notification;
import com.project.drone_missions.data.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    /** Guards the overdue scheduler so each mission is notified only once. */
    boolean existsByUser_IdAndMission_IdAndType(Long userId, Long missionId, NotificationType type);
}
