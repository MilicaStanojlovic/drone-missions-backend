package com.project.drone_missions.business.exception.auth;

import com.project.drone_missions.business.UnauthorizedException;

/**
 * Thrown on login when the email is unknown or the password does not match.
 * Mapped to 401. The message is intentionally generic — it never reveals whether
 * the email exists — to avoid account enumeration.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
