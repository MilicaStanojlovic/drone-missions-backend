package com.project.drone_missions.web.controller.mission;

import com.project.drone_missions.business.service.mission.MissionService;
import com.project.drone_missions.business.service.rating.RatingService;
import com.project.drone_missions.business.service.rating.RatingSummary;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.web.mapper.mission.MissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mission.user_id is nullable by design (V4: rows created before authentication existed),
 * so every response path has to survive a mission with no owner.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissionControllerTest {

    @Mock
    private MissionService service;

    @Mock
    private MissionMapper mapper;

    @Mock
    private RatingService ratingService;

    private MissionController controller;

    @BeforeEach
    void setUp() {
        controller = new MissionController(service, mapper, ratingService);
        when(ratingService.summariesFor(any())).thenReturn(Map.of());
        when(ratingService.summaryFor(any())).thenReturn(RatingSummary.NONE);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Mission legacyMission() {
        Mission mission = new Mission();
        mission.setId(1L);
        mission.setStatus(MissionStatus.PUBLISHED);
        mission.setDesigner(null);
        return mission;
    }

    @Test
    void theOpenFeedSurvivesAMissionWithNoOwner() {
        when(service.findOpen(null, null, null)).thenReturn(List.of(legacyMission()));

        assertThatCode(() -> controller.findAll(null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void adminListWrapsThePageAndSurvivesAMissionWithNoOwner() {
        Pageable pageable = PageRequest.of(0, 20);
        when(service.searchAll("orchard", pageable))
                .thenReturn(new PageImpl<>(List.of(legacyMission()), pageable, 1));

        assertThat(controller.adminList("orchard", pageable).getBody().getContent()).hasSize(1);
        verify(service).searchAll("orchard", pageable);
        verify(service, never()).findOpen(any(), any(), any());
    }

    @Test
    void aSingleMissionWithNoOwnerStillRenders() {
        when(service.findById(anyLong(), anyLong())).thenReturn(legacyMission());

        assertThatCode(() -> controller.findById(1L, 7L)).doesNotThrowAnyException();
    }

    @Test
    void ownedMissionsAreStillReturnedForARealOwner() {
        Mission owned = legacyMission();
        owned.setDesigner(user(7L));
        when(service.findOwnedBy(7L)).thenReturn(List.of(owned));

        assertThat(controller.findMine(7L).getBody()).hasSize(1);
    }
}
