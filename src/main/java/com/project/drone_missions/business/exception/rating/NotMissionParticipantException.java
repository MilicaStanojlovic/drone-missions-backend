package com.project.drone_missions.business.exception.rating;

import com.project.drone_missions.business.ForbiddenException;

/**
 * Thrown when someone who was neither the designer nor the awarded pilot tries to rate.
 * Mapped to 403 Forbidden.
 */
public class NotMissionParticipantException extends ForbiddenException {

    public NotMissionParticipantException(Long missionId) {
        super("You did not take part in mission %d, so you cannot rate it".formatted(missionId));
    }
}
