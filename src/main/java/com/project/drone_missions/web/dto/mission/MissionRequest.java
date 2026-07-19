package com.project.drone_missions.web.dto.mission;

import com.project.drone_missions.data.model.MissionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MissionRequest(
        @NotBlank String name,
        @Size(max = 2000) String description,
        @NotNull MissionStatus status,
        Instant startTime,
        Instant endTime
) {
}
