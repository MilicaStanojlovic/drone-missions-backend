package com.project.drone_missions.business.exception.rating;

import com.project.drone_missions.business.ConflictException;

/**
 * Thrown on a second rating from the same person for the same mission. A rating is
 * written once and never changed. Mapped to 409 Conflict.
 */
public class AlreadyRatedException extends ConflictException {

    public AlreadyRatedException(Long missionId) {
        super("You have already rated mission %d".formatted(missionId));
    }
}
