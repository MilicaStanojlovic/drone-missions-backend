package com.project.drone_missions.data.access;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The data access layer for missions — the only way business and web code reaches mission
 * rows. {@code MissionRepository} is an implementation detail behind this interface and must
 * not be injected anywhere else.
 *
 * <p>That rule is what makes caching safe rather than merely convenient. Missions are written
 * from two services ({@code MissionService} and {@code BidService}), so a cache owned by
 * either one would be stale the moment the other wrote. Routing every read <em>and</em> write
 * through one interface means the object holding the cache observes every write, and
 * invalidation cannot be forgotten at a call site.
 *
 * <h2>Choosing between findById and findFresh</h2>
 * The distinction is load-bearing, not stylistic. {@link Mission} has no {@code @Version}
 * column, so JPA cannot detect a stale write. If a mutating flow read a cached, detached copy
 * and passed it to {@link #save}, {@code merge} would write <em>every</em> field of that stale
 * copy back to the row — including {@code status} and {@code awardedPilotId}, which an edit
 * deliberately never touches. A cached copy saying {@code PUBLISHED}, merged over a row that a
 * bid has since moved to {@code BIDDING}, silently reverts the status with nothing to catch it.
 *
 * <p>So: <strong>read-only flows use {@link #findById}; anything that will call {@link #save}
 * uses {@link #findFresh}.</strong>
 */
public interface MissionDataAccess {

    /**
     * Look up a mission for a read-only flow. May be served from cache, and may therefore
     * return a detached copy rather than a managed entity.
     *
     * <p>Never pass the result to {@link #save} — use {@link #findFresh} for that.
     */
    Optional<Mission> findById(Long id);

    /**
     * Look up a mission that is about to be modified. Always hits the database, returns a
     * managed entity, and evicts any cached copy on the way through.
     */
    Optional<Mission> findFresh(Long id);

    /** The open marketplace, filtered. Ordered newest-created first. */
    List<Mission> findOpen(OpenMissionQuery query);

    /** Missions created by this user. */
    List<Mission> findByUserId(Long userId);

    /** Missions awarded to this pilot. */
    List<Mission> findByAwardedPilotId(Long pilotId);

    /** Awarded missions whose flight window has ended — the overdue scheduler's candidates. */
    List<Mission> findOverdue(Collection<MissionStatus> statuses, Instant endedBefore);

    /** Persist a new or modified mission. Invalidates any cached copy. */
    Mission save(Mission mission);

    /** Delete a mission. Invalidates any cached copy. */
    void delete(Mission mission);
}
