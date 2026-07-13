package com.project.drone_missions.business.exception.auth;

import com.project.drone_missions.business.ConflictException;

/**
 * Thrown on registration when the email is already taken. Mapped to 409 Conflict.
 * The message deliberately does not confirm which field clashed beyond the email
 * the caller already supplied.
 */
public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException(String email) {
        super("Email %s is already registered".formatted(email));
    }
}
