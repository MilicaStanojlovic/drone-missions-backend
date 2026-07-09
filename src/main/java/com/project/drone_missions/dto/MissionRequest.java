package com.project.drone_missions.dto;

import com.project.drone_missions.model.MissionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record MissionRequest(
        @NotBlank String name,
        String description,
        @NotNull MissionStatus status,
        Instant startTime,
        Instant endTime
) {
}
