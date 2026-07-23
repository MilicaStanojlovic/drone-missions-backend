package com.project.drone_missions.business.exception.bid;

import com.project.drone_missions.business.NotFoundException;

/**
 * Thrown when a bid cannot be found by id — including when it exists but
 * belongs to another pilot: a bid a caller may not touch must not be
 * distinguishable from one that does not exist (mirrors the mission pattern).
 */
public class BidNotFoundException extends NotFoundException {

    public BidNotFoundException(Long id) {
        super("Bid %d not found".formatted(id));
    }
}
