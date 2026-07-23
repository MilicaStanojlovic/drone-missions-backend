package com.project.drone_missions.business.exception.bid;

import com.project.drone_missions.business.ConflictException;

/**
 * Thrown when a bid action conflicts with the current state: bidding on a
 * closed mission, updating or withdrawing a bid that has already been decided,
 * or awarding a mission that is already awarded.
 */
public class BidConflictException extends ConflictException {

    public BidConflictException(String message) {
        super(message);
    }
}
