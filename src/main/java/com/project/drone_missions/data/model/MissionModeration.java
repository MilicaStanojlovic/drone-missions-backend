package com.project.drone_missions.data.model;

/**
 * Admin moderation state of a mission, orthogonal to the lifecycle {@link MissionStatus}
 * so hiding never disturbs where the mission is in its life. Admin removal is a real
 * delete, not a state.
 */
public enum MissionModeration {
    /** The normal state — visible wherever the lifecycle status allows. */
    VISIBLE,
    /** Pulled from the pilot feed but still with its designer. Reversible. */
    HIDDEN
}
