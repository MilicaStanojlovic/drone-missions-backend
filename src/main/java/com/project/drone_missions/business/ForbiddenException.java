package com.project.drone_missions.business;

/**
 * Base type for "authenticated but not allowed" errors (mapped to 403). Do not
 * throw this directly — extend it with a domain-specific exception (e.g.
 * MissionAccessDeniedException) so the exception type conveys what was denied,
 * and a single handler can map the whole family to 403.
 */
public abstract class ForbiddenException extends RuntimeException {

    protected ForbiddenException(String message) {
        super(message);
    }
}
