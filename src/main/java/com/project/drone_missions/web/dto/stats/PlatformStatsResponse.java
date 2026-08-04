package com.project.drone_missions.web.dto.stats;

import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PlatformStatsResponse(
        Map<MissionStatus, Long> missionsByStatus,
        long activePilots,
        long bidCount,
        BigDecimal bidAmountTotal,
        long suspendedUsers,
        Map<UserRole, Long> usersByRole,
        List<TopMissionResponse> topMissionsByBids
) {

    public record TopMissionResponse(String name, long bids) {
    }
}
