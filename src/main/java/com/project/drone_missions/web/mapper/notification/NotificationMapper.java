package com.project.drone_missions.web.mapper.notification;

import com.project.drone_missions.data.model.Notification;
import com.project.drone_missions.web.dto.notification.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getMission() == null ? null : notification.getMission().getId(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }
}
