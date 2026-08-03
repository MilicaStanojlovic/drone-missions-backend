package com.project.drone_missions.business.exception.user;

import com.project.drone_missions.business.ForbiddenException;

/**
 * Thrown when a suspended account attempts an action moderation forbids —
 * creating missions, bidding, or executing awarded work. Mapped to 403.
 */
public class UserSuspendedException extends ForbiddenException {

    public UserSuspendedException() {
        super("This account is suspended and cannot perform this action");
    }
}
