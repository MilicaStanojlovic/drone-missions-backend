package com.project.drone_missions.web.controller.stats;

import com.project.drone_missions.business.service.stats.PlatformStats;
import com.project.drone_missions.business.service.stats.PlatformStatsService;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.web.dto.stats.PlatformStatsResponse;
import com.project.drone_missions.web.mapper.stats.PlatformStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformStatsControllerTest {

    @Mock
    private PlatformStatsService service;

    private PlatformStatsController controller;

    @BeforeEach
    void setUp() {
        controller = new PlatformStatsController(service, new PlatformStatsMapper());
    }

    @Test
    void overviewMapsTheStatsIntoTheResponse() {
        when(service.overview()).thenReturn(new PlatformStats(
                Map.of(MissionStatus.PUBLISHED, 2L), 7L, 57L, new BigDecimal("12345.50"),
                3L, Map.of(UserRole.PILOT, 31L),
                List.of(new PlatformStats.TopMission("Orchard survey", 9L))));

        PlatformStatsResponse body = controller.overview().getBody();

        assertThat(body.missionsByStatus()).containsEntry(MissionStatus.PUBLISHED, 2L);
        assertThat(body.activePilots()).isEqualTo(7L);
        assertThat(body.bidCount()).isEqualTo(57L);
        assertThat(body.bidAmountTotal()).isEqualByComparingTo("12345.50");
        assertThat(body.suspendedUsers()).isEqualTo(3L);
        assertThat(body.usersByRole()).containsEntry(UserRole.PILOT, 31L);
        assertThat(body.topMissionsByBids())
                .containsExactly(new PlatformStatsResponse.TopMissionResponse("Orchard survey", 9L));
    }
}
