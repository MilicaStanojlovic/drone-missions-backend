package com.project.drone_missions.business;

/**
 * Base type for "authentication failed" errors surfaced from the business layer
 * (mapped to 401), e.g. bad login credentials. Missing/invalid bearer tokens are
 * handled earlier by the security layer, not through this type. Do not throw this
 * directly — extend it with a domain-specific exception.
 */
public abstract class UnauthorizedException extends RuntimeException {

    protected UnauthorizedException(String message) {
        super(message);
    }
}
