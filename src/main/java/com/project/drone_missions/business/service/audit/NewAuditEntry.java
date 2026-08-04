package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.Rating;
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

    /** {@code updated} — place() upserts, and "raised an existing bid" is worth telling apart. */
    public static NewAuditEntry bidPlaced(Long pilotId, Bid bid, boolean updated) {
        return new NewAuditEntry(pilotId, UserRole.PILOT, AuditAction.BID_PLACED,
                AuditTargetType.BID, bid.getId(),
                "%s on %s%s".formatted(bid.getAmount(), quoted(bid.getMission().getName()),
                        updated ? " (updated)" : ""));
    }

    public static NewAuditEntry bidWithdrawn(Long pilotId, Bid bid) {
        return new NewAuditEntry(pilotId, UserRole.PILOT, AuditAction.BID_WITHDRAWN,
                AuditTargetType.BID, bid.getId(),
                "%s on %s".formatted(bid.getAmount(), quoted(bid.getMission().getName())));
    }

    public static NewAuditEntry bidAccepted(Long designerId, Bid bid) {
        return new NewAuditEntry(designerId, UserRole.DESIGNER, AuditAction.BID_ACCEPTED,
                AuditTargetType.BID, bid.getId(),
                "%s on %s".formatted(bid.getAmount(), quoted(bid.getMission().getName())));
    }

    public static NewAuditEntry userRegistered(User user) {
        return self(user, AuditAction.USER_REGISTERED);
    }

    public static NewAuditEntry userLoggedIn(User user) {
        return self(user, AuditAction.USER_LOGGED_IN);
    }

    /** The rater's role is derived: a mission's rater is either its designer or its pilot. */
    public static NewAuditEntry ratingCreated(Long raterId, Mission mission, Rating rating) {
        UserRole role = raterId.equals(mission.getDesignerId()) ? UserRole.DESIGNER : UserRole.PILOT;
        return new NewAuditEntry(raterId, role, AuditAction.RATING_CREATED,
                AuditTargetType.RATING, rating.getId(),
                "%d/5 on %s".formatted(rating.getScore(), quoted(mission.getName())));
    }

    public static NewAuditEntry userSuspended(Long adminId, User target) {
        return new NewAuditEntry(adminId, UserRole.ADMIN, AuditAction.USER_SUSPENDED,
                AuditTargetType.USER, target.getId(), quoted(target.getUsername()));
    }

    public static NewAuditEntry userReactivated(Long adminId, User target) {
        return new NewAuditEntry(adminId, UserRole.ADMIN, AuditAction.USER_REACTIVATED,
                AuditTargetType.USER, target.getId(), quoted(target.getUsername()));
    }

    private static NewAuditEntry self(User user, AuditAction action) {
        return new NewAuditEntry(user.getId(), user.getRole(), action,
                AuditTargetType.USER, user.getId(), quoted(user.getUsername()));
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
