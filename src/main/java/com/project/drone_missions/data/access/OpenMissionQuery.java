package com.project.drone_missions.data.access;

import com.project.drone_missions.data.model.MissionStatus;

import java.time.Instant;
import java.util.Set;

/**
 * The normalised filters for one open-marketplace search. Blank text filters and an absent
 * date arrive here as {@code null}, already trimmed, and the day-boundary instants have
 * already been resolved in the caller's timezone — this record carries values, not policy.
 *
 * <p>A record rather than a {@code Specification}: it has value equality, so it can serve
 * directly as a cache key. A {@code Specification} is a lambda and never equals another.
 *
 * @param statuses the mission statuses considered "open" — supplied by the caller so the
 *                 domain decision stays in the business layer
 * @param location substring match on location, already lowercased and trimmed, or null for no
 *                 filter — normalising the value here (not just at the SQL layer) is what
 *                 keeps two case-different searches for the same thing from becoming two
 *                 distinct entries in the list cache
 * @param keyword  substring match on name or description, already lowercased and trimmed, or
 *                 null, normalised for the same reason as {@code location}
 * @param from     inclusive lower bound the flight window must reach, or null
 * @param to       exclusive upper bound the flight window must start before, or null
 */
public record OpenMissionQuery(Set<MissionStatus> statuses, String location,
                               String keyword, Instant from, Instant to) {

    public OpenMissionQuery {
        statuses = Set.copyOf(statuses);
    }
}
