package com.project.drone_missions.data.model;

public enum NotificationType {
    BID_ACCEPTED,      // the pilot's bid was accepted — they won the mission
    BID_REJECTED,      // the pilot's bid was rejected (another bid was accepted)
    MISSION_OVERDUE,   // a won mission's end date has passed — has the flight ended?
    MISSION_CANCELLED  // the designer cancelled a mission the pilot had won
}
