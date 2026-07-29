package com.project.drone_missions.web.mapper.rating;

import com.project.drone_missions.data.model.Rating;
import com.project.drone_missions.web.dto.rating.RatingResponse;
import org.springframework.stereotype.Component;

/**
 * No repositories: the relations carry the names, so the mapper reads them off the entity
 * rather than looking each one up.
 */
@Component
public class RatingMapper {

    public RatingResponse toResponse(Rating rating) {  // da se ne poziva iz repo.. servisi
        return new RatingResponse(
                rating.getId(),
                rating.getMission().getId(),
                rating.getMission().getName(),
                rating.getRater().getId(),
                rating.getRater().getUsername(),
                rating.getRatee().getId(),
                rating.getScore(),
                rating.getComment(),
                rating.getCreatedAt()
        );
    }
}
