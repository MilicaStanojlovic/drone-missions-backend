package com.project.drone_missions.web.dto.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RatingRequest(
        @NotNull @Min(1) @Max(5) Short score,
        @Size(max = 500) String comment
) {
}
