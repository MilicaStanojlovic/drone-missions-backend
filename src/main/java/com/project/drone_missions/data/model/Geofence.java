package com.project.drone_missions.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * A mission's permitted flight area, stored as {@code jsonb}. Exactly one shape:
 * a {@code CIRCLE} ({@code center} + {@code radiusMeters}) or a {@code POLYGON}
 * (an ordered ring of {@code points}). Unused fields are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Geofence(
        @NotNull GeofenceType type,
        @Valid GeoPoint center,
        @Positive Double radiusMeters,
        @Valid List<GeoPoint> points
) {

    /** Enforce that the fields present actually match the declared shape. */
    @JsonIgnore
    @AssertTrue(message = "a CIRCLE needs center + radiusMeters; a POLYGON needs at least 3 points")
    public boolean isConsistent() {
        if (type == GeofenceType.CIRCLE) {
            return center != null && radiusMeters != null;
        }
        if (type == GeofenceType.POLYGON) {
            return points != null && points.size() >= 3;
        }
        return false;
    }
}
