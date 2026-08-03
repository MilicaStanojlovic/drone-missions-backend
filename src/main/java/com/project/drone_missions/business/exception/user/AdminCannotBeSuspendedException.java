package com.project.drone_missions.business.exception.user;

import com.project.drone_missions.business.ConflictException;

/**
 * Thrown when an admin tries to suspend an ADMIN account — moderation applies to
 * marketplace roles only. Mapped to 409 Conflict.
 */
public class AdminCannotBeSuspendedException extends ConflictException {

    public AdminCannotBeSuspendedException(Long id) {
        super("User %d is an admin and cannot be suspended".formatted(id));
    }
}
