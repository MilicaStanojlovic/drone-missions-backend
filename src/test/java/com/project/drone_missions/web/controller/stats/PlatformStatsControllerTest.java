package com.project.drone_missions.web.controller.stats;

import com.project.drone_missions.business.service.stats.PlatformStats;
import com.project.drone_missions.business.service.stats.PlatformStatsService;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.web.dto.stats.PlatformStatsResponse;
import com.project.drone_missions.web.mapper.stats.PlatformStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
                Map.of(MissionStatus.PUBLISHED, 2L), 7L, 57L, new BigDecimal("12345.50")));

        PlatformStatsResponse body = controller.overview().getBody();

        assertThat(body.missionsByStatus()).containsEntry(MissionStatus.PUBLISHED, 2L);
        assertThat(body.activePilots()).isEqualTo(7L);
        assertThat(body.bidCount()).isEqualTo(57L);
        assertThat(body.bidAmountTotal()).isEqualByComparingTo("12345.50");
    }
}
