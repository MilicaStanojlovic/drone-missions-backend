package com.project.drone_missions.data.access;

import com.github.benmanes.caffeine.cache.Cache;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Caches mission reads with Spring's cache abstraction — the framework-idiomatic twin of
 * {@link CachingMissionDao}. Both implement {@link MissionDao} and decorate
 * {@link JpaMissionDao}; exactly one is active, chosen by the {@code cache-spring}
 * profile (see {@code config.SpringCacheConfig}). Nothing in {@code MissionService} or
 * {@code BidService} knows which one it is talking to.
 *
 * <p>Two implementations exist on purpose. The project's Spring-first rule says the framework
 * should win where it fits; the hand-written cache predates that judgement and encodes
 * behaviour the annotations cannot express. Keeping both runnable turns that into a question
 * that can be answered by running the app instead of argued about.
 *
 * <h2>What is cached</h2>
 * Two named caches: {@code missions} (id to entity) and {@code missionLists} (query to result
 * list). Unlike the hand-written cache, lists hold <em>whole missions</em>, not ordered ids —
 * {@code @Cacheable} caches whatever the method returns, and reproducing the id-list indirection
 * would mean a second bean and self-invocation gymnastics for no gain in idiomatic form.
 *
 * <h2>How this differs from {@link CachingMissionDao}</h2>
 * These are the costs of staying idiomatic. None is a defect to fix here; they are the point of
 * the comparison.
 * <ul>
 *   <li><b>Writes clear everything.</b> {@code @CacheEvict(allEntries = true)} is the only way
 *       to express "any write can change which missions a query returns", so a single save
 *       throws away the expensive entity rows too. The hand-written cache discards small id
 *       arrays and keeps the rows.</li>
 *   <li><b>Cached entities are shared, not copied.</b> The stored instance is handed to every
 *       caller, so mutating a returned {@link Mission} in place corrupts the entry for everyone.
 *       Safe today only because every write flow goes through {@link #findFresh}, which is never
 *       served from cache, and the mappers only read. The hand-written cache copies in and out
 *       via {@code Mission}'s all-args constructor and cannot be corrupted this way.</li>
 *   <li><b>No transaction-aware eviction.</b> Eviction happens when the method returns, not on
 *       {@code afterCompletion}. Inside the two {@code @Transactional} flows (mission cancel,
 *       bid accept) a concurrent reader can repopulate an entry with the pre-commit row, and
 *       that entry outlives the commit until TTL. Spring's own
 *       {@code TransactionAwareCacheDecorator} does not close this: it defers to
 *       {@code afterCommit}, which would leave a stale entry in place after a rollback.</li>
 *   <li><b>Bounding is LRU, not refusal.</b> A full Caffeine cache evicts a least-recently-used
 *       entry to admit the new one; {@code TtlCache} refuses the new value and keeps what it
 *       has. Caffeine's behaviour is the more conventional one.</li>
 * </ul>
 *
 * <h2>Proxy rules</h2>
 * The annotations are applied by a proxy, so a call from one method of this class to another
 * would bypass the cache entirely. Every method here goes straight to {@code delegate} — keep
 * it that way.
 *
 * <p>Shares the single-instance limitation documented on {@link CachingMissionDao}: two
 * JVMs would hold two Caffeine caches and neither would see the other's writes.
 */
@Slf4j
public class SpringCacheMissionDao implements MissionDao {

    /** Mission id to entity. */
    public static final String MISSIONS = "missions";

    /** Query to the list of missions it returned. */
    public static final String MISSION_LISTS = "missionLists";

    private final MissionDao delegate;
    private final CacheManager cacheManager;

    public SpringCacheMissionDao(MissionDao delegate, CacheManager cacheManager) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
    }

    // ---- reads ----

    /**
     * {@code unless} guards on {@code #result == null}, not {@code isEmpty()}: Spring unwraps an
     * {@code Optional} return value, so inside the expression {@code #result} is the
     * {@link Mission} itself, or null when the Optional was empty. Absent ids therefore stay
     * uncached — they are the 404 path, the key space is caller-supplied and unbounded, and a
     * negative entry would go stale the moment that mission is created.
     *
     * <p>{@code condition} keeps a null id away from the cache key; Caffeine rejects null keys.
     */
    @Override
    @Cacheable(value = MISSIONS, key = "#id", condition = "#id != null", unless = "#result == null")
    public Optional<Mission> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return delegate.findById(id);
    }

    /**
     * {@code beforeInvocation = true} so the copy is dropped up front, matching
     * {@link CachingMissionDao#findFresh}. The default evicts only after a successful
     * return, which would both serve a stale copy to a concurrent reader while the caller
     * mutates, and leave the stale entry in place if the load throws.
     */
    @Override
    @CacheEvict(value = MISSIONS, key = "#id", condition = "#id != null", beforeInvocation = true)
    public Optional<Mission> findFresh(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return delegate.findFresh(id);
    }

    /** Keyed by the query record itself — value equality is why it is a record and not a Specification. */
    @Override
    @Cacheable(value = MISSION_LISTS, key = "#query")
    public List<Mission> findOpen(OpenMissionQuery query) {
        return delegate.findOpen(query);
    }

    /** Prefixed key: the owner and pilot lists share one cache with the feed queries. */
    @Override
    @Cacheable(value = MISSION_LISTS, key = "'byUser:' + #userId")
    public List<Mission> findByUserId(Long userId) {
        return delegate.findByUserId(userId);
    }

    @Override
    @Cacheable(value = MISSION_LISTS, key = "'byPilot:' + #pilotId")
    public List<Mission> findByAwardedPilotId(Long pilotId) {
        return delegate.findByAwardedPilotId(pilotId);
    }

    /** Not cached: a once-a-day sweep gains nothing and would only add invalidation surface. */
    @Override
    public List<Mission> findOverdue(Collection<MissionStatus> statuses, Instant endedBefore) {
        return delegate.findOverdue(statuses, endedBefore);
    }

    /** Not cached, for the same reasons as {@link CachingMissionDao#findAll()}. */
    @Override
    public List<Mission> findAll() {
        return delegate.findAll();
    }

    // ---- writes ----

    /**
     * Evicts after the delegate returns, and never caches the result: inside a transaction
     * {@code @UpdateTimestamp} has not been applied yet, so the returned row does not match what
     * will be committed. Read-through only.
     */
    @Override
    @CacheEvict(value = {MISSIONS, MISSION_LISTS}, allEntries = true)
    public Mission save(Mission mission) {
        return delegate.save(mission);
    }

    @Override
    @CacheEvict(value = {MISSIONS, MISSION_LISTS}, allEntries = true)
    public void delete(Mission mission) {
        delegate.delete(mission);
    }

    // ---- observability ----

    /**
     * Mirrors {@link CachingMissionDao#sweepAndReport()} on the same schedule and with the
     * same field names, so switching profiles produces comparable log lines. There is no actuator
     * on the classpath, so a log line is the only cache visibility either implementation has.
     *
     * <p>Two counters have no Caffeine equivalent and are omitted rather than faked:
     * {@code expired} (Caffeine folds expiry into {@code evictionCount}) and {@code rejected}
     * (Caffeine evicts instead of refusing).
     */
    @Scheduled(fixedDelayString = "${app.cache.mission.report-interval:PT5M}")
    void sweepAndReport() { // Caffeine logovanje
        log.info("mission cache (spring): entities[{}] lists[{}]",
                statsOf(MISSIONS), statsOf(MISSION_LISTS));
    }

    /** Exposed for tests and debugging. */
    String statsOf(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            return "unavailable";
        }
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        // Caffeine performs maintenance lazily; this is the analogue of TtlCache.purgeExpired().
        nativeCache.cleanUp();
        // Fully qualified: Caffeine's CacheStats collides with this package's own record.
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();
        return formatStats(stats.hitCount(), stats.missCount(),
                nativeCache.estimatedSize(), stats.evictionCount());
    }

    /**
     * Renders the counters exactly as {@link CacheStats#toString()} does, so a log line from
     * either implementation reads the same way.
     *
     * <p>The ratio is recomputed rather than taken from {@code CacheStats.hitRate()}: Caffeine
     * reports {@code 1.0} for a cache nothing has asked for yet, where {@link CacheStats} reports
     * {@code 0.0}. An untouched cache claiming a perfect hit rate would make the two profiles
     * look different when they are not.
     */
    static String formatStats(long hits, long misses, long size, long evictions) {
        long requests = hits + misses;
        double ratio = requests == 0 ? 0.0 : (double) hits / requests;
        return "hits=%d misses=%d ratio=%.2f size=%d evictions=%d"
                .formatted(hits, misses, ratio, size, evictions);
    }
}
