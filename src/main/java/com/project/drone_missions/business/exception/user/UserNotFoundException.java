package com.project.drone_missions.business.exception.user;

import com.project.drone_missions.business.NotFoundException;

/**
 * Thrown when a user cannot be found by id. Self-documenting: the type alone
 * conveys the error context, without inspecting the message.
 */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long id) {
        super("User %d not found".formatted(id));
    }
}
