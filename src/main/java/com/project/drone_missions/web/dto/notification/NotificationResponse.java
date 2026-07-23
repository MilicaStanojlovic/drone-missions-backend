package com.project.drone_missions.web.dto.notification;

import com.project.drone_missions.data.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long missionId,
        boolean read,
        Instant createdAt
) {
}
