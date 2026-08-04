package com.project.drone_missions.business.service.stats;

import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide counts for the admin overview. The maps carry every status/role,
 * zero-filled. A business value object beside its service, like
 * {@code RatingSummary} — not a web DTO.
 */
public record PlatformStats(
        Map<MissionStatus, Long> missionsByStatus,
        long activePilots,
        long bidCount,
        BigDecimal bidAmountTotal,
        long suspendedUsers,
        Map<UserRole, Long> usersByRole,
        List<TopMission> topMissionsByBids
) {

    /** One bar of the overview's most-bid-on chart — name only, never an id. */
    public record TopMission(String name, long bids) {
    }
}
