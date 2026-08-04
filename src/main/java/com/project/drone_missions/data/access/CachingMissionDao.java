package com.project.drone_missions.data.access;

import com.project.drone_missions.config.MissionCacheProperties;
import com.project.drone_missions.data.model.Geofence;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caches mission reads in front of another {@link MissionDao}. A decorator rather than
 * a cache embedded in a service: because every mission read and write already funnels through
 * this interface, this object observes all of them and invalidation cannot be forgotten at a
 * call site — including {@code BidService}, which writes missions without ever touching
 * {@code MissionService}.
 *
 * <h2>What is cached</h2>
 * Two caches with different jobs:
 * <ul>
 *   <li><b>entities</b> — mission id to a detached copy of the row.</li>
 *   <li><b>lists</b> — a query to the <em>ordered ids</em> it returned, never to entities.</li>
 * </ul>
 * Storing ids keeps entity freshness in exactly one place, and makes list invalidation cheap:
 * a write throws away small id arrays while the expensive rows survive. If a cached id list
 * cannot be fully resolved from the entity cache, the query is simply re-run — one database
 * call, the same cost as no cache at all.
 *
 * <h2>Copies, in and out</h2>
 * Nothing managed by JPA is ever stored, and the stored instance is never handed out. Cached
 * missions are built with the all-args constructor rather than setters on purpose: adding a
 * field to {@link Mission} then breaks this file at compile time instead of silently dropping
 * the new field from the copy.
 *
 * <p>One consequence to know about: a mission returned from the cache has an immutable
 * {@code waypoints} list, so mutating it in place throws. Nothing does that today (an edit
 * replaces the list reference), and failing loudly beats corrupting a shared entry.
 *
 * <h2>Known limits</h2>
 * <ul>
 *   <li><b>Single instance only.</b> Two JVMs would hold two caches and neither would see the
 *       other's writes, leaving stale reads until TTL. Nothing enforces one instance today —
 *       the {@code @Scheduled} overdue sweep already assumes it. The fix, if a second instance
 *       ever appears, is a shared cache behind this same interface.</li>
 *   <li><b>A benign load race.</b> A reader that misses can have its database load land after a
 *       concurrent writer's eviction, re-inserting a just-superseded row. It is bounded by TTL
 *       and harmless in practice, because no write path ever reads from the cache — see
 *       {@link MissionDao#findFresh}. Closing it properly needs load-generation
 *       counters, which is not worth the complexity here.</li>
 * </ul>
 */
@Slf4j
public class CachingMissionDao implements MissionDao {

    /** Cache key for the owner/pilot list queries, kept distinct from an {@link OpenMissionQuery}. */
    private record OwnerKey(String kind, Long id) {
    }

    private final MissionDao delegate;
    private final TtlCache<Long, Mission> entities;
    private final TtlCache<Object, List<Long>> lists;

    public CachingMissionDao(MissionDao delegate,
                              MissionCacheProperties properties,
                              Clock clock) {
        this.delegate = delegate;
        this.entities = new TtlCache<>(properties.ttl(), properties.maxSize(), clock);
        this.lists = new TtlCache<>(properties.ttl(), properties.listMaxSize(), clock);
    }

    // ---- reads ----

    @Override
    public Optional<Mission> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<Mission> cached = entities.get(id);
        if (cached.isPresent()) {
            return cached.map(CachingMissionDao::fromCache);
        }
        Optional<Mission> loaded = delegate.findById(id);
        // Absent ids are deliberately not cached: they are the 404 path, ids come from the
        // caller so the key space is unbounded, and a negative entry would go stale the
        // moment that mission is created.
        loaded.ifPresent(this::cacheEntity);
        return loaded;
    }

    @Override
    public Optional<Mission> findFresh(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        // Drop any copy up front so nothing stale is served while the caller mutates.
        entities.evict(id);
        return delegate.findFresh(id);
    }

    @Override
    public List<Mission> findOpen(OpenMissionQuery query) {
        return cachedList(query, () -> delegate.findOpen(query));
    }

    @Override
    public List<Mission> findByUserId(Long userId) {
        return cachedList(new OwnerKey("byUser", userId), () -> delegate.findByUserId(userId));
    }

    @Override
    public List<Mission> findByAwardedPilotId(Long pilotId) {
        return cachedList(new OwnerKey("byPilot", pilotId), () -> delegate.findByAwardedPilotId(pilotId));
    }

    /** Not cached: a once-a-day sweep gains nothing and would only add invalidation surface. */
    @Override
    public List<Mission> findOverdue(Collection<MissionStatus> statuses, Instant endedBefore) {
        return delegate.findOverdue(statuses, endedBefore);
    }

    /** Not cached: a rare admin-only view is not worth widening the invalidation surface. */
    @Override
    public Page<Mission> searchAll(String pattern, Pageable pageable) {
        return delegate.searchAll(pattern, pageable);
    }

    /** Not cached: a rare admin-only stats view is not worth widening the invalidation surface. */
    @Override
    public Map<MissionStatus, Long> countByStatus() {
        return delegate.countByStatus();
    }

    /**
     * Feed membership changed without a mission write (a designer was suspended or
     * reactivated). Only the id arrays go; the entity rows are still correct.
     */
    @Override
    public void invalidateLists() {
        lists.clear();
    }

    // ---- writes ----

    @Override
    public Mission save(Mission mission) {
        Mission saved = delegate.save(mission);
        // Never cache `saved`: inside a transaction @UpdateTimestamp has not been applied yet,
        // so it would store a row that does not match what will be committed. Read-through only.
        invalidate(saved.getId());
        return saved;
    }

    @Override
    public void delete(Mission mission) {
        Long id = mission.getId();
        delegate.delete(mission);
        invalidate(id);
    }

    // ---- invalidation ----

    /**
     * Drop the mission and every cached list, now and again once any surrounding transaction
     * finishes.
     *
     * <p>The second pass matters: a concurrent reader can repopulate the cache mid-transaction
     * with the pre-commit row, which would then outlive the commit. Clearing on
     * {@code afterCompletion} covers commit and rollback alike — unlike Spring's own
     * {@code TransactionAwareCacheDecorator}, which defers to {@code afterCommit} because it
     * defers a <em>put</em>; deferring an eviction only on success would leave the stale entry
     * in place after a rollback.
     *
     * <p>The guard is required, not defensive: most writes here run outside a transaction
     * (only mission cancellation and bid acceptance are {@code @Transactional}), and
     * registering a synchronization without an active one throws.
     */
    private void invalidate(Long id) {
        evictNow(id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    evictNow(id);
                }
            });
        }
    }

    private void evictNow(Long id) {
        if (id != null) {
            entities.evict(id);
        }
        // Any write can change which missions a query returns, so membership indexes all go.
        // They are only id arrays; the costly entity rows are untouched.
        lists.clear();
    }

    // ---- helpers ----

    private List<Mission> cachedList(Object key, java.util.function.Supplier<List<Mission>> loader) {
        Optional<List<Long>> cachedIds = lists.get(key);
        if (cachedIds.isPresent()) {
            List<Mission> hydrated = hydrate(cachedIds.get());
            if (hydrated != null) {
                return hydrated;
            }
        }
        List<Mission> loaded = loader.get();
        List<Long> ids = new ArrayList<>(loaded.size());
        for (Mission mission : loaded) {
            cacheEntity(mission);
            ids.add(mission.getId());
        }
        lists.put(key, List.copyOf(ids));
        return loaded;
    }

    /** Rebuild a list from the entity cache, or null if any member is no longer cached. */
    private List<Mission> hydrate(List<Long> ids) {
        List<Mission> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Optional<Mission> cached = entities.get(id);
            if (cached.isEmpty()) {
                return null;
            }
            result.add(fromCache(cached.get()));
        }
        return result;
    }

    private void cacheEntity(Mission mission) {
        if (mission.getId() != null) {
            entities.put(mission.getId(), toCacheable(mission));
        }
    }

    /**
     * The copy that goes into the cache: detached from JPA, collections frozen so every copy
     * handed out can share them. The designer/awardedPilot relations are shared rather than
     * copied — the one way left for a caller to reach into a cached entry, by mutating the
     * User it was handed. Copying them here would cache stale account rows instead.
     */
    private static Mission toCacheable(Mission m) {
        return new Mission(
                m.getId(), m.getName(), m.getDescription(), m.getStatus(), m.getModeration(),
                m.getDesigner(), m.getAwardedPilot(),
                m.getStartTime(), m.getEndTime(),
                m.getLocation(), m.getBiddingDeadline(),
                m.getWaypoints() == null ? null : List.copyOf(m.getWaypoints()),
                copyGeofence(m.getGeofence()),
                m.getCreatedAt(), m.getUpdatedAt());
    }

    /** The copy handed to a caller: a fresh shell over parts that are already immutable. */
    private static Mission fromCache(Mission cached) {
        return new Mission(
                cached.getId(), cached.getName(), cached.getDescription(), cached.getStatus(),
                cached.getModeration(),
                cached.getDesigner(), cached.getAwardedPilot(),
                cached.getStartTime(), cached.getEndTime(),
                cached.getLocation(), cached.getBiddingDeadline(),
                cached.getWaypoints(),
                cached.getGeofence(),
                cached.getCreatedAt(), cached.getUpdatedAt());
    }

    private static Geofence copyGeofence(Geofence g) {
        if (g == null) {
            return null;
        }
        return new Geofence(g.type(), g.center(), g.radiusMeters(),
                g.points() == null ? null : List.copyOf(g.points()));
    }

    // ---- observability ----

    /** Reclaims expired entries early and makes the hit rate visible without extra tooling. */
    @Scheduled(fixedDelayString = "${app.cache.mission.report-interval:PT5M}")
    void sweepAndReport() {
        int purged = entities.purgeExpired() + lists.purgeExpired();
        log.info("mission cache: entities[{}] lists[{}] purged={}",
                entities.stats(), lists.stats(), purged);
    }

    /** Exposed for tests and debugging. */
    CacheStats entityStats() {
        return entities.stats();
    }

    /** Exposed for tests and debugging. */
    CacheStats listStats() {
        return lists.stats();
    }
}
