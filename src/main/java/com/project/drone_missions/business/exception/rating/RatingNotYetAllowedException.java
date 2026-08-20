package com.project.drone_missions.business.exception.rating;

import com.project.drone_missions.business.ConflictException;
import com.project.drone_missions.data.model.MissionStatus;

/**
 * Thrown when a mission has not reached COMPLETED, so there is nothing to rate yet.
 * Mapped to 409 Conflict.
 */
public class RatingNotYetAllowedException extends ConflictException {

    public RatingNotYetAllowedException(Long missionId, MissionStatus status) {
        super("Mission %d is %s — it can only be rated once completed".formatted(missionId, status));
    }
}
