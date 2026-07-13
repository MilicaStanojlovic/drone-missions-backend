package com.project.drone_missions.business.exception.mission;

import com.project.drone_missions.business.NotFoundException;

/**
 * Thrown when a mission cannot be found by id. Self-documenting: the type alone
 * conveys the error context, without inspecting the message.
 */
public class MissionNotFoundException extends NotFoundException {

    public MissionNotFoundException(Long id) {
        super("Mission %d not found".formatted(id));
    }
}
