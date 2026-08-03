package com.project.drone_missions.data.model;

/**
 * Which side of the marketplace an account is on. Chosen at registration and never
 * changed afterwards — an account is exactly one of these, so someone who both lists
 * work and flies it needs two accounts.
 *
 * <p>Because the role is immutable it can safely ride in the JWT: a token can never
 * go stale against the database.
 */
public enum UserRole {
    /** Defines the work and chooses who flies it. Creates and owns missions. */
    DESIGNER,
    /** Finds available work, bids on it, and carries it out. */
    PILOT,
    /** Oversees the platform: sees every account and mission. Seeded, never self-registered. */
    ADMIN
}
