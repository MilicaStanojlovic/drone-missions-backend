package com.project.drone_missions.web.dto.rating;

import java.time.Instant;

/** Carries the rater and mission display names so the client never shows raw identifiers. */
public record RatingResponse(
        Long id,
        Long missionId,
        String missionName,
        Long raterId,
        String raterName,
        Long rateeId,
        Short score,
        String comment,
        Instant createdAt
) {
}
