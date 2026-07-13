package com.project.drone_missions.business.exception.mission;

import com.project.drone_missions.business.ForbiddenException;

/**
 * Thrown when a user tries to modify or delete a mission they do not own.
 * Mapped to 403 Forbidden.
 */
public class MissionAccessDeniedException extends ForbiddenException {

    public MissionAccessDeniedException(Long missionId) {
        super("You are not allowed to modify mission %d".formatted(missionId));
    }
}
