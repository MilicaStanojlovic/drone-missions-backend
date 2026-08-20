package com.project.drone_missions.data.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * A geographic point in WGS84 degrees. Not a JPA entity — it is serialized to/from
 * JSON as part of a mission's {@code waypoints} and {@code geofence} (both stored in
 * {@code jsonb} columns). The bounds are validated when it rides in on a request.
 */
public record GeoPoint(
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lng
) {
}
