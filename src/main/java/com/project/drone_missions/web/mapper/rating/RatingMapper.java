package com.project.drone_missions.web.mapper.rating;

import com.project.drone_missions.data.access.MissionDataAccess;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.Rating;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.UserRepository;
import com.project.drone_missions.web.dto.rating.RatingResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Enriches ratings with the rater and mission display names, as BidMapper does for bids —
 * per-row lookups, fine at this scale.
 */
@Component
@AllArgsConstructor
public class RatingMapper {

    private final MissionDataAccess missionDataAccess;
    private final UserRepository userRepository;

    public RatingResponse toResponse(Rating rating) {  // da se ne poziva iz repo.. servisi
        String missionName = missionDataAccess.findById(rating.getMissionId())
                .map(Mission::getName)
                .orElse(null);
        String raterName = userRepository.findById(rating.getRaterId())
                .map(User::getUsername)
                .orElse(null);
        return new RatingResponse(
                rating.getId(),
                rating.getMissionId(),
                missionName,
                rating.getRaterId(),
                raterName,
                rating.getRateeId(),
                rating.getScore(),
                rating.getComment(),
                rating.getCreatedAt()
        );
    }
}
