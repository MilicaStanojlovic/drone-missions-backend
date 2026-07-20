package com.project.drone_missions.business;

/**
 * Base type for "conflicts with existing state" errors (mapped to 409). Do not
 * throw this directly — extend it with a domain-specific exception (e.g.
 * EmailAlreadyExistsException) so the exception type itself documents the clash,
 * and a single handler can map the whole family to 409.
 */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
