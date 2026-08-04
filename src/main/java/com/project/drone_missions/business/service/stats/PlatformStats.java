package com.project.drone_missions.business.service.stats;

import com.project.drone_missions.data.model.MissionStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Platform-wide counts for the admin overview. {@code missionsByStatus} carries
 * all statuses, zero-filled. A business value object beside its service, like
 * {@code RatingSummary} — not a web DTO.
 */
public record PlatformStats(
        Map<MissionStatus, Long> missionsByStatus,
        long activePilots,
        long bidCount,
        BigDecimal bidAmountTotal
) {
}
