package com.project.drone_missions.business.service.rating;

import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.exception.rating.AlreadyRatedException;
import com.project.drone_missions.business.exception.rating.NotMissionParticipantException;
import com.project.drone_missions.business.exception.rating.RatingNotYetAllowedException;
import com.project.drone_missions.data.access.MissionDataAccess;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.Rating;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.RatingRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    private static final Long MISSION_ID = 7L;
    private static final Long DESIGNER_ID = 1L;
    private static final Long PILOT_ID = 2L;
    private static final Long OUTSIDER_ID = 99L;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MissionDataAccess missionDataAccess;

    @Mock
    private UserRepository userRepository;

    private RatingService service;

    @BeforeEach
    void setUp() {
        service = new RatingService(ratingRepository, missionDataAccess, userRepository);
        lenient().when(userRepository.getReferenceById(any(Long.class)))
                .thenAnswer(i -> user(i.getArgument(0)));
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Mission completedMission() {
        return missionWith(MissionStatus.COMPLETED);
    }

    private Mission missionWith(MissionStatus status) {
        Mission mission = new Mission();
        mission.setId(MISSION_ID);
        mission.setStatus(status);
        mission.setDesigner(user(DESIGNER_ID));
        mission.setAwardedPilot(user(PILOT_ID));
        return mission;
    }

    private void givenMission(Mission mission) {
        when(missionDataAccess.findById(MISSION_ID)).thenReturn(Optional.of(mission));
    }

    private RatingRepository.RateeSummary summaryRow(Long rateeId, double average, long total) {
        return new RatingRepository.RateeSummary() {
            @Override
            public Long getRateeId() {
                return rateeId;
            }

            @Override
            public Double getAverage() {
                return average;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private Rating captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void designerRatingResolvesToTheAwardedPilot() {
        givenMission(completedMission());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(i -> i.getArgument(0));

        service.create(MISSION_ID, DESIGNER_ID, (short) 5, "great flying");

        Rating saved = captureSaved();
        assertThat(saved.getRater().getId()).isEqualTo(DESIGNER_ID);
        assertThat(saved.getRatee().getId()).isEqualTo(PILOT_ID);
        assertThat(saved.getScore()).isEqualTo((short) 5);
        assertThat(saved.getComment()).isEqualTo("great flying");
    }

    @Test
    void pilotRatingResolvesToTheDesigner() {
        givenMission(completedMission());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(i -> i.getArgument(0));

        service.create(MISSION_ID, PILOT_ID, (short) 4, null);

        Rating saved = captureSaved();
        assertThat(saved.getRater().getId()).isEqualTo(PILOT_ID);
        assertThat(saved.getRatee().getId()).isEqualTo(DESIGNER_ID);
    }

    @Test
    void someoneWhoTookNoPartCannotRate() {
        givenMission(completedMission());

        assertThatThrownBy(() -> service.create(MISSION_ID, OUTSIDER_ID, (short) 5, null))
                .isInstanceOf(NotMissionParticipantException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void anUnfinishedMissionCannotBeRated() {
        givenMission(missionWith(MissionStatus.IN_PROGRESS));

        assertThatThrownBy(() -> service.create(MISSION_ID, PILOT_ID, (short) 5, null))
                .isInstanceOf(RatingNotYetAllowedException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void ratingTwiceIsRejected() {
        givenMission(completedMission());
        when(ratingRepository.existsByMission_IdAndRater_Id(MISSION_ID, PILOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(MISSION_ID, PILOT_ID, (short) 5, null))
                .isInstanceOf(AlreadyRatedException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void ratingAMissionThatDoesNotExistIsANotFound() {
        when(missionDataAccess.findById(MISSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(MISSION_ID, PILOT_ID, (short) 5, null))
                .isInstanceOf(MissionNotFoundException.class);
    }

    /** A mission completed before anyone was awarded leaves the designer with nobody to rate. */
    @Test
    void designerCannotRateWhenNoPilotWasAwarded() {
        Mission mission = completedMission();
        mission.setAwardedPilot(null);
        givenMission(mission);

        assertThatThrownBy(() -> service.create(MISSION_ID, DESIGNER_ID, (short) 5, null))
                .isInstanceOf(NotMissionParticipantException.class);
    }

    @Test
    void onlyParticipantsMayReadAMissionsRatings() {
        givenMission(completedMission());

        assertThatThrownBy(() -> service.forMission(MISSION_ID, OUTSIDER_ID))
                .isInstanceOf(NotMissionParticipantException.class);
    }

    @Test
    void summariesAreKeyedByRateeAndSkipUnratedUsers() {
        when(ratingRepository.summariesFor(Set.of(DESIGNER_ID, PILOT_ID)))
                .thenReturn(List.of(summaryRow(DESIGNER_ID, 4.5d, 2L)));

        Map<Long, RatingSummary> summaries = service.summariesFor(List.of(DESIGNER_ID, PILOT_ID));

        assertThat(summaries).containsOnlyKeys(DESIGNER_ID);
        assertThat(summaries.get(DESIGNER_ID).average()).isEqualTo(4.5d);
        assertThat(summaries.get(DESIGNER_ID).count()).isEqualTo(2L);
    }

    @Test
    void anUnratedUserSummarisesAsNone() {
        when(ratingRepository.summariesFor(Set.of(PILOT_ID))).thenReturn(List.of());

        assertThat(service.summaryFor(PILOT_ID)).isEqualTo(RatingSummary.NONE);
    }

    @Test
    void noIdsMeansNoQuery() {
        assertThat(service.summariesFor(List.of())).isEmpty();
        verify(ratingRepository, never()).summariesFor(any());
    }
    /** A mission with no owner (V4 legacy row) must not blow up the summary lookup. */
    @Test
    void summaryForANullUserIsNone() {
        assertThat(service.summaryFor(null)).isEqualTo(RatingSummary.NONE);
    }

    @Test
    void nullIdsAreSkippedRatherThanQueried() {
        assertThat(service.summariesFor(java.util.Arrays.asList((Long) null, null))).isEmpty();
        verify(ratingRepository, never()).summariesFor(any());
    }
}
