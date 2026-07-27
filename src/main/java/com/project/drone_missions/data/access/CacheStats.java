package com.project.drone_missions.data.access;

/**
 * An immutable snapshot of a {@link TtlCache}'s counters. Exists so the "this improves
 * performance" claim is observable rather than assumed.
 *
 * @param hits        reads served from the cache
 * @param misses      reads that found nothing (including entries found expired)
 * @param puts        values actually stored
 * @param evictions   entries removed explicitly, by invalidation
 * @param expirations entries removed because their TTL had passed
 * @param rejections  puts refused because the cache was full — see {@link TtlCache}
 * @param size        entries currently held, including any not yet purged
 */
public record CacheStats(long hits, long misses, long puts, long evictions,
                         long expirations, long rejections, int size) {

    /** Share of reads served from cache, 0.0 when nothing has been read yet. */
    public double hitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    @Override
    public String toString() {
        return "hits=%d misses=%d ratio=%.2f size=%d evictions=%d expired=%d rejected=%d"
                .formatted(hits, misses, hitRatio(), size, evictions, expirations, rejections);
    }
}
