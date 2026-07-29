package com.project.drone_missions.business.service.notification;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.NotificationType;

import java.util.Objects;

/**
 * The data needed to raise one in-app notification — a parameter object rather than five
 * positional arguments, so the two {@code Long} ids and the two {@code String}s cannot be
 * transposed at a call site without the compiler noticing.
 *
 * <p>The static factories carry the wording of each notification type. They exist so that
 * text lives in exactly one place instead of being rebuilt inline by {@code BidService},
 * {@code MissionService} and {@code OverdueNotificationScheduler}.
 *
 * <p>This is a business-layer value object, not a web DTO: it deliberately does not live
 * under {@code web.dto}, which would make {@code business} depend on {@code web}.
 */
public record NewNotification(Long userId, NotificationType type, String title,
                              String message, Mission mission) {

    public NewNotification {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
    }

    /** The pilot won: their bid was accepted and the mission is theirs. */
    public static NewNotification bidAccepted(Long pilotId, Mission mission) {
        return new NewNotification(pilotId, NotificationType.BID_ACCEPTED,
                "Bid accepted",
                "Your bid on \"%s\" was accepted — the mission is yours.".formatted(mission.getName()),
                mission);
    }

    /** The pilot lost: another bid was chosen for this mission. */
    public static NewNotification bidRejected(Long pilotId, Mission mission) {
        return new NewNotification(pilotId, NotificationType.BID_REJECTED,
                "Bid not selected",
                "Your bid on \"%s\" wasn't selected.".formatted(mission.getName()),
                mission);
    }

    /** The designer cancelled a mission this pilot had already won. */
    public static NewNotification missionCancelled(Long pilotId, Mission mission) {
        return new NewNotification(pilotId, NotificationType.MISSION_CANCELLED,
                "Mission cancelled",
                "\"%s\" was cancelled by the designer.".formatted(mission.getName()),
                mission);
    }

    /** The awarded mission's flight window has passed without being marked finished. */
    public static NewNotification missionOverdue(Long pilotId, Mission mission) {
        return new NewNotification(pilotId, NotificationType.MISSION_OVERDUE,
                "Has your flight ended?",
                "\"%s\" has passed its end date. Mark it finished if the flight is done."
                        .formatted(mission.getName()),
                mission);
    }
}
