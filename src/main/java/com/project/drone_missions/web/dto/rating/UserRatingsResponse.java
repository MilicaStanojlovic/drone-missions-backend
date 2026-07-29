package com.project.drone_missions.web.dto.rating;

import java.util.List;

/** A profile's whole reputation in one call: the headline numbers plus what people wrote. */
public record UserRatingsResponse(
        double average,
        long count,
        List<RatingResponse> ratings
) {
}
