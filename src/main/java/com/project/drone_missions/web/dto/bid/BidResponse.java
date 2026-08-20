package com.project.drone_missions.web.dto.bid;

import com.project.drone_missions.data.model.BidStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries the mission and pilot display names alongside the ids so the client
 * never has to show (or re-fetch) raw identifiers.
 */
public record BidResponse(
        Long id,
        Long missionId,
        String missionName,
        Long pilotId,
        String pilotName,
        BigDecimal amount,
        String message,
        BidStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
