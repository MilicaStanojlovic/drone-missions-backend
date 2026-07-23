package com.project.drone_missions.business.exception.mission;

import com.project.drone_missions.business.ConflictException;

/**
 * Thrown when a mission action conflicts with its current lifecycle status —
 * e.g. trying to complete a mission that has not started yet or is already done.
 * Mapped to 409 Conflict.
 */
public class MissionConflictException extends ConflictException {

    public MissionConflictException(String message) {
        super(message);
    }
}
