package com.project.drone_missions.data.model;

public enum MissionStatus {
    DRAFT,        // designer is still planning; not visible to pilots
    PUBLISHED,    // mission is finished and visible to pilots
    BIDDING,      // pilots are submitting offers
    AWARDED,      // designer has chosen a pilot
    IN_PROGRESS,  // chosen pilot is carrying out the flight
    COMPLETED,    // work is finished
    CANCELLED     // stopped instead of continuing (can occur at appropriate points)
}
