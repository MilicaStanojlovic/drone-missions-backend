package com.project.drone_missions.business;

/**
 * Base type for "resource not found" errors. Do not throw this directly —
 * extend it with a domain-specific exception (e.g. MissionNotFoundException in
 * business.mission) so the exception type itself documents what was missing,
 * and a single handler can catch the whole family and map it to 404.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
