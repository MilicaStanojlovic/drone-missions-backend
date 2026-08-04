package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;

import java.util.Objects;

/**
 * Parameter object for one audit row, mirroring {@code NewNotification}. The
 * factories own each action's role (safe: restates the {@code @PreAuthorize}
 * gate) and pair it with the right target type; {@code details} snapshots
 * context so the row outlives a deleted target.
 */
public record NewAuditEntry(Long actorId, UserRole actorRole, AuditAction action,
                            AuditTargetType targetType, Long targetId, String details) {

    public NewAuditEntry {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
    }

    public static NewAuditEntry missionCreated(Long designerId, Mission mission) {
        return mission(designerId, UserRole.DESIGNER, AuditAction.MISSION_CREATED, mission);
    }

    public static NewAuditEntry missionUpdated(Long designerId, Mission mission) {
        return mission(designerId, UserRole.DESIGNER, AuditAction.MISSION_UPDATED, mission);
    }

    public static NewAuditEntry missionDeleted(Long designerId, Mission mission) {
        return mission(designerId, UserRole.DESIGNER, AuditAction.MISSION_DELETED, mission);
    }

    public static NewAuditEntry missionCancelled(Long designerId, Mission mission) {
        return mission(designerId, UserRole.DESIGNER, AuditAction.MISSION_CANCELLED, mission);
    }

    public static NewAuditEntry missionStarted(Long pilotId, Mission mission) {
        return mission(pilotId, UserRole.PILOT, AuditAction.MISSION_STARTED, mission);
    }

    public static NewAuditEntry missionCompleted(Long pilotId, Mission mission) {
        return mission(pilotId, UserRole.PILOT, AuditAction.MISSION_COMPLETED, mission);
    }

    public static NewAuditEntry missionHidden(Long adminId, Mission mission) {
        return mission(adminId, UserRole.ADMIN, AuditAction.MISSION_HIDDEN, mission);
    }

    public static NewAuditEntry missionUnhidden(Long adminId, Mission mission) {
        return mission(adminId, UserRole.ADMIN, AuditAction.MISSION_UNHIDDEN, mission);
    }

    public static NewAuditEntry missionRemoved(Long adminId, Mission mission) {
        return mission(adminId, UserRole.ADMIN, AuditAction.MISSION_REMOVED, mission);
    }

    public static NewAuditEntry missionRestored(Long adminId, Mission mission) {
        return mission(adminId, UserRole.ADMIN, AuditAction.MISSION_RESTORED, mission);
    }

    public static NewAuditEntry userSuspended(Long adminId, User target) {
        return new NewAuditEntry(adminId, UserRole.ADMIN, AuditAction.USER_SUSPENDED,
                AuditTargetType.USER, target.getId(), quoted(target.getUsername()));
    }

    public static NewAuditEntry userReactivated(Long adminId, User target) {
        return new NewAuditEntry(adminId, UserRole.ADMIN, AuditAction.USER_REACTIVATED,
                AuditTargetType.USER, target.getId(), quoted(target.getUsername()));
    }

    private static NewAuditEntry mission(Long actorId, UserRole role, AuditAction action,
                                         Mission mission) {
        return new NewAuditEntry(actorId, role, action, AuditTargetType.MISSION,
                mission.getId(), quoted(mission.getName()));
    }

    private static String quoted(String name) {
        return "\"%s\"".formatted(name);
    }
}
