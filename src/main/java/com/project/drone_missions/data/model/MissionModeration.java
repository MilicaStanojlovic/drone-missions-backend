package com.project.drone_missions.data.model;

/**
 * Admin moderation state of a mission, orthogonal to the lifecycle {@link MissionStatus}
 * so hiding or restoring never disturbs where the mission is in its life.
 */
public enum MissionModeration {
    /** The normal state — visible wherever the lifecycle status allows. */
    VISIBLE,
    /** Pulled from the pilot feed but still with its designer. Reversible. */
    HIDDEN,
    /** Withdrawn from the platform entirely, owner included. Reversible — not a delete. */
    REMOVED
}
