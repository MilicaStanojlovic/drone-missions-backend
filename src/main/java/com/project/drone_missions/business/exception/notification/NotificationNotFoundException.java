package com.project.drone_missions.business.exception.notification;

import com.project.drone_missions.business.NotFoundException;

/**
 * Thrown when a notification cannot be found by id for the current user —
 * including when it belongs to someone else (masked as not-found so ids can't
 * be probed, mirroring the bid/mission pattern). Mapped to 404.
 */
public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException(Long id) {
        super("Notification %d not found".formatted(id));
    }
}
