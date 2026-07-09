package com.project.drone_missions.dto;

import com.project.drone_missions.model.MissionStatus;

import java.time.Instant;

public record MissionResponse(
        Long id,
        String name,
        String description,
        MissionStatus status,
        Instant startTime,
        Instant endTime,
        Instant createdAt,
        Instant updatedAt
) {
}
