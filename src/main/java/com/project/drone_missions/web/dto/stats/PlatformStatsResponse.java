package com.project.drone_missions.web.dto.stats;

import com.project.drone_missions.data.model.MissionStatus;

import java.math.BigDecimal;
import java.util.Map;

public record PlatformStatsResponse(
        Map<MissionStatus, Long> missionsByStatus,
        long activePilots,
        long bidCount,
        BigDecimal bidAmountTotal
) {
}
