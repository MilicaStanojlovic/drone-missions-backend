package com.project.drone_missions.business.service.rating;

import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.exception.rating.AlreadyRatedException;
import com.project.drone_missions.business.exception.rating.NotMissionParticipantException;
import com.project.drone_missions.business.exception.rating.RatingNotYetAllowedException;
import com.project.drone_missions.data.access.MissionDataAccess;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.Rating;
import com.project.drone_missions.data.repository.RatingRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MissionDataAccess missionDataAccess;
    private final UserRepository userRepository;

    /**
     * The mission row is the only membership record there is, so it answers both "may this
     * person rate?" and "who are they rating?" at once.
     */
    public Rating create(Long missionId, Long raterId, Short score, String comment) {
        // Read-only: rating never writes the mission, so a cached copy is fine.
        Mission mission = missionDataAccess.findById(missionId)
                .orElseThrow(() -> new MissionNotFoundException(missionId));

        if (mission.getStatus() != MissionStatus.COMPLETED) {
            throw new RatingNotYetAllowedException(missionId, mission.getStatus());
        }
        if (ratingRepository.existsByMission_IdAndRater_Id(missionId, raterId)) {
            throw new AlreadyRatedException(missionId);
        }

        Rating rating = new Rating();
        rating.setMission(mission);
        // getReferenceById: setting an FK needs a reference, not a loaded row.
        rating.setRater(userRepository.getReferenceById(raterId));
        rating.setRatee(userRepository.getReferenceById(counterpartOf(mission, raterId)));
        rating.setScore(score);
        rating.setComment(comment);
        return ratingRepository.save(rating);
    }

    /** Both ratings for a mission, so a participant can see whether they have rated yet. */
    public List<Rating> forMission(Long missionId, Long callerId) {
        Mission mission = missionDataAccess.findById(missionId)
                .orElseThrow(() -> new MissionNotFoundException(missionId));
        requireParticipant(mission, callerId);
        return ratingRepository.findByMission_IdOrderByCreatedAtDesc(missionId);
    }

    public List<Rating> receivedBy(Long userId) {
        return ratingRepository.findByRatee_IdOrderByCreatedAtDesc(userId);
    }

    /** Null is a legitimate owner id — mission.user_id is nullable for pre-auth rows. */
    public RatingSummary summaryFor(Long userId) {
        if (userId == null) {
            return RatingSummary.NONE;
        }
        return summariesFor(List.of(userId)).getOrDefault(userId, RatingSummary.NONE);
    }

    /**
     * One query for a whole page of missions. Users with no ratings are absent from the map
     * rather than present as zero, so callers decide what "unrated" should look like.
     */
    public Map<Long, RatingSummary> summariesFor(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()); // TODO

        if (ids.isEmpty()) {
            return Map.of();
        }
        return ratingRepository.summariesFor(ids).stream()
                .collect(Collectors.toMap(
                        RatingRepository.RateeSummary::getRateeId,
                        row -> new RatingSummary(row.getAverage(), row.getTotal())));
    }

    private Long counterpartOf(Mission mission, Long raterId) {
        if (raterId.equals(mission.getUserId()) && mission.getAwardedPilotId() != null) {
            return mission.getAwardedPilotId();
        }
        if (raterId.equals(mission.getAwardedPilotId())) {
            return mission.getUserId();
        }
        throw new NotMissionParticipantException(mission.getId());
    }

    private void requireParticipant(Mission mission, Long callerId) {
        if (!callerId.equals(mission.getUserId()) && !callerId.equals(mission.getAwardedPilotId())) {
            throw new NotMissionParticipantException(mission.getId());
        }
    }
}
