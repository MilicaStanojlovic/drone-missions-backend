package com.project.drone_missions.web.dto.mission;

import com.project.drone_missions.data.model.GeoPoint;
import com.project.drone_missions.data.model.Geofence;
import com.project.drone_missions.data.model.MissionModeration;
import com.project.drone_missions.data.model.MissionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MissionResponse(
        Long id,
        String name,
        String description,
        MissionStatus status,
        MissionModeration moderation,
        Long userId,
        String designerEmail,
        String designerName,
        boolean designerSuspended,
        double designerRating,
        long designerRatingCount,
        Long awardedPilotId,
        Instant startTime,
        Instant endTime,
        String location,
        LocalDate biddingDeadline,
        List<GeoPoint> waypoints,
        Geofence geofence,
        Instant createdAt,
        Instant updatedAt
) {
}
