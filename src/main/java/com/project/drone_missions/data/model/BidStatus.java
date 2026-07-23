package com.project.drone_missions.data.model;

public enum BidStatus {
    PENDING,   // placed and waiting for the designer's decision; may be updated or withdrawn
    ACCEPTED,  // the designer chose this bid — its pilot won the mission
    REJECTED   // another bid was accepted (rejection is automatic on award)
}
