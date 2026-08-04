package com.project.drone_missions.web.dto.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.UserRole;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorUsername,
        UserRole actorRole,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        String details,
        Instant createdAt
) {
}
