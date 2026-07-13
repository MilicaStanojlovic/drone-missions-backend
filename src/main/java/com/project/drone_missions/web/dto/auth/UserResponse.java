package com.project.drone_missions.web.dto.auth;

import java.time.Instant;

/** Public view of a user account — never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        Instant createdAt
) {
}
