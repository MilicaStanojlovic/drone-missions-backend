package com.project.drone_missions.web.dto.auth;

import com.project.drone_missions.data.model.UserRole;

import java.time.Instant;

/** Public view of a user account — never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        boolean suspended,
        Instant createdAt
) {
}
