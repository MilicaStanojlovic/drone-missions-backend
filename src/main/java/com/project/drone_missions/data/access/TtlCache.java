package com.project.drone_missions.data.access;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * A small hand-written cache: time-to-live expiry, a size bound, and hit/miss counters.
 * Deliberately free of Spring, JPA and any third-party library — it is a plain data structure.
 *
 * <h2>Expiry</h2>
 * Expiry is checked lazily on read, so a stale entry is never served even if nothing ever
 * sweeps. {@link #purgeExpired()} only reclaims memory sooner; it is an optimisation, never a
 * correctness requirement.
 *
 * <h2>Bounding: admission, not eviction</h2>
 * When the cache is full, {@link #put} first drops expired entries; if it is still full the
 * <em>new</em> value is refused and counted as a rejection. Existing entries are never
 * discarded to make room.
 *
 * <p>This is a deliberate simplification, not an oversight. A true LRU needs access-ordering,
 * which means mutating a linked list on every <em>read</em> under a global lock — trading
 * lock-free reads for a capacity problem this dataset does not have. Refusing admission is
 * O(1), keeps reads lock-free, self-heals as entries expire, and has a useful security
 * property: a caller flooding the cache with junk keys cannot evict the hot entries.
 *
 * <h2>Concurrency</h2>
 * Backed by a {@link ConcurrentHashMap}. Reads are lock-free. Note that {@code get} does
 * <em>not</em> load values: callers miss, load outside any lock, then {@code put}. That is
 * intentional — {@code computeIfAbsent} would hold a bin lock across the caller's database
 * round trip, serialising unrelated keys and throwing {@code IllegalStateException} on any
 * re-entrant access. Two threads may therefore load the same key concurrently; the load is
 * idempotent, so this is harmless and much cheaper than locking around I/O.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAtEpochMilli) {
        boolean isExpired(long nowEpochMilli) {
            return nowEpochMilli >= expiresAtEpochMilli;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long ttlMillis; // Schedular
    private final int maxSize;

    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder puts = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder expirations = new LongAdder();
    private final LongAdder rejections = new LongAdder();

    /**
     * @param ttl     how long an entry stays valid after being stored
     * @param maxSize the most entries held at once; further puts are rejected
     * @param clock   the time source — inject a fixed or offset clock in tests so TTL can be
     *                exercised without sleeping
     */
    public TtlCache(Duration ttl, int maxSize, Clock clock) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, was " + maxSize);
        }
        this.ttlMillis = ttl.toMillis();
        this.maxSize = maxSize;
        this.clock = clock;
    }

    /** The cached value, or empty if absent or expired. */
    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            misses.increment();
            return Optional.empty();
        }
        if (entry.isExpired(clock.millis())) {
            // remove(key, entry) so a concurrent refresh of the same key is not discarded.
            if (entries.remove(key, entry)) {
                expirations.increment();
            }
            misses.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(entry.value());
    }

    /** Store a value, unless the cache is full of live entries. */
    public void put(K key, V value) {
        if (entries.size() >= maxSize && !entries.containsKey(key)) {
            purgeExpired();
            if (entries.size() >= maxSize) {
                rejections.increment();
                return;
            }
        }
        entries.put(key, new Entry<>(value, clock.millis() + ttlMillis));
        puts.increment();
    }

    /** Drop one entry. Does nothing if it was not cached. */
    public void evict(K key) {
        if (entries.remove(key) != null) {
            evictions.increment();
        }
    }

    /** Drop every entry. Counters are left alone so lifetime statistics stay meaningful. */
    public void clear() {
        int dropped = entries.size();
        entries.clear();
        evictions.add(dropped);
    }

    /**
     * Remove entries whose TTL has passed.
     *
     * @return how many were removed
     */
    public int purgeExpired() {
        long now = clock.millis();
        int removed = 0;
        for (Iterator<Map.Entry<K, Entry<V>>> it = entries.entrySet().iterator(); it.hasNext(); )  { // TODO stream
            Map.Entry<K, Entry<V>> e = it.next();
            if (e.getValue().isExpired(now)) {
                it.remove();
                removed++;
            }
        }
        expirations.add(removed);
        return removed;
    }

    /** Entries currently held, including any expired but not yet purged. */
    public int size() {
        return entries.size();
    }

    /** A point-in-time snapshot of the counters. */
    public CacheStats stats() {
        return new CacheStats(hits.sum(), misses.sum(), puts.sum(), evictions.sum(),
                expirations.sum(), rejections.sum(), entries.size());
    }

    @Override
    public String toString() {
        return "TtlCache[" + stats() + "]";
    }
}
