package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.data.model.User;

/**
 * Outcome of a successful login: the issued JWT and the authenticated user.
 * Internal to the layers (never serialized) — the controller puts the token in the
 * response header and maps the user to a {@code UserResponse} for the body.
 */
public record LoginResult(String token, User user) {
}
