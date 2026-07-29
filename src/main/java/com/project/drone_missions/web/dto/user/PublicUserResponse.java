package com.project.drone_missions.web.dto.user;

import com.project.drone_missions.data.model.UserRole;

import java.time.Instant;

/**
 * What anyone may see about another account. Deliberately excludes email — that is
 * personal data the marketplace has no reason to hand to strangers.
 */
public record PublicUserResponse(
        Long id,
        String username,
        UserRole role,
        Instant createdAt
) {
}
