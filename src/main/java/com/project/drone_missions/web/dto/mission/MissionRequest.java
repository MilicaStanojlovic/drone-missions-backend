package com.project.drone_missions.web.dto.mission;

import com.project.drone_missions.data.model.Geofence;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.Waypoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MissionRequest(
        @NotBlank String name,
        @Size(max = 2000) String description,
        @NotNull MissionStatus status,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        // ---- flight plan ----
        @Size(max = 255) String location,
        LocalDate biddingDeadline,
        // a flight path needs a start and an end — reject a missing path or a single dangling point
        @NotNull(message = "a flight path needs at least 2 waypoints")
        @Size(min = 2, message = "a flight path needs at least 2 waypoints")
        @Valid List<Waypoint> waypoints,
        @Valid Geofence geofence
) {
}
