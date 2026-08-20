package com.project.drone_missions.business.exception.auth;

import com.project.drone_missions.business.ForbiddenException;

/**
 * Thrown when registration asks for the ADMIN role. Admin accounts are seeded by
 * migration, never self-registered. Mapped to 403.
 */
public class AdminRegistrationNotAllowedException extends ForbiddenException {

    public AdminRegistrationNotAllowedException() {
        super("Admin accounts cannot be self-registered");
    }
}
