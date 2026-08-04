package com.project.drone_missions.data.model;

/**
 * One value per user intent, not per side effect — cancelling a mission rejects
 * its bids, but only MISSION_CANCELLED is recorded.
 */
public enum AuditAction {
    MISSION_CREATED,
    MISSION_UPDATED,
    MISSION_DELETED,
    MISSION_STARTED,
    MISSION_COMPLETED,
    MISSION_CANCELLED,
    MISSION_HIDDEN,
    MISSION_UNHIDDEN,
    MISSION_REMOVED,
    /** No longer produced (removal is a hard delete now); kept so historical rows deserialize. */
    MISSION_RESTORED,
    BID_PLACED,
    BID_WITHDRAWN,
    BID_ACCEPTED,
    USER_REGISTERED,
    USER_LOGGED_IN,
    USER_SUSPENDED,
    USER_REACTIVATED,
    RATING_CREATED
}
