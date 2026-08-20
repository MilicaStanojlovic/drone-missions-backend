package com.project.drone_missions.business.service.rating;

/** A user's reputation in the two numbers every view of it needs. */
public record RatingSummary(double average, long count) {

    public static final RatingSummary NONE = new RatingSummary(0.0, 0);
}
