package com.project.drone_missions.web.mapper.stats;

import com.project.drone_missions.business.service.stats.PlatformStats;
import com.project.drone_missions.web.dto.stats.PlatformStatsResponse;
import org.springframework.stereotype.Component;

@Component
public class PlatformStatsMapper {

    public PlatformStatsResponse toResponse(PlatformStats stats) {
        return new PlatformStatsResponse(
                stats.missionsByStatus(),
                stats.activePilots(),
                stats.bidCount(),
                stats.bidAmountTotal());
    }
}
