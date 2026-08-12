package com.project.drone_missions.data.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * One stop on a mission's flight plan: where to fly, how high, and what to do there.
 * Not a JPA entity — serialized to/from the {@code waypoints} {@code jsonb} column.
 * The new fields are nullable so waypoints written before them still deserialize;
 * their required-ness is enforced by validation on requests only.
 */
@ValidWaypointAction
public record Waypoint(
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lng,
        // above ground level, in meters; 120 m is the legal ceiling
        @NotNull @Positive @DecimalMax("120") Double altitude,
        @NotNull WaypointAction action,
        Integer hoverDurationSeconds
) {
}
