package com.project.drone_missions.data.access;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit — no Spring context and no database, so this runs anywhere. Time is driven by
 * a mutable {@link Clock} rather than {@code Thread.sleep}, which keeps the TTL assertions
 * exact and instant.
 */
class TtlCacheTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    /** A hand-cranked clock. {@code Clock.millis()} delegates to {@link #instant()}. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final MutableClock clock = new MutableClock();

    private TtlCache<String, String> cache(int maxSize) {
        return new TtlCache<>(TTL, maxSize, clock);
    }

    @Test
    void missOnUnknownKey() {
        TtlCache<String, String> cache = cache(10);

        assertThat(cache.get("absent")).isEmpty();
        assertThat(cache.stats().misses()).isEqualTo(1);
        assertThat(cache.stats().hits()).isZero();
    }

    @Test
    void hitAfterPut() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");

        assertThat(cache.get("k")).contains("v");
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().puts()).isEqualTo(1);
    }

    @Test
    void entryIsStillLiveOneMillisecondBeforeExpiry() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");

        clock.advance(TTL.minusMillis(1));

        assertThat(cache.get("k")).contains("v");
    }

    @Test
    void entryExpiresExactlyAtTtlAndIsRemoved() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");

        clock.advance(TTL);

        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
        assertThat(cache.stats().expirations()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void evictRemovesAndCounts() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");

        cache.evict("k");

        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.stats().evictions()).isEqualTo(1);
    }

    @Test
    void evictingAnAbsentKeyCountsNothing() {
        TtlCache<String, String> cache = cache(10);

        cache.evict("never-there");

        assertThat(cache.stats().evictions()).isZero();
    }

    /** The decision that separates this from an LRU: a full cache refuses the newcomer. */
    @Test
    void whenFullTheNewEntryIsRejectedAndExistingEntriesSurvive() {
        TtlCache<String, String> cache = cache(2);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.put("c", "3");

        assertThat(cache.get("c")).isEmpty();
        assertThat(cache.get("a")).contains("1");
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.stats().rejections()).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void overwritingAnExistingKeyIsAllowedEvenWhenFull() {
        TtlCache<String, String> cache = cache(2);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.put("a", "updated");

        assertThat(cache.get("a")).contains("updated");
        assertThat(cache.stats().rejections()).isZero();
    }

    @Test
    void expiredEntriesFreeSpaceForNewOnes() {
        TtlCache<String, String> cache = cache(2);
        cache.put("a", "1");
        cache.put("b", "2");

        clock.advance(TTL);
        cache.put("c", "3");

        assertThat(cache.get("c")).contains("3");
        assertThat(cache.stats().rejections()).isZero();
    }

    @Test
    void purgeExpiredRemovesOnlyExpiredEntries() {
        TtlCache<String, String> cache = cache(10);
        cache.put("old", "1");
        clock.advance(TTL.minusMinutes(1));
        cache.put("new", "2");

        clock.advance(Duration.ofMinutes(1));
        int purged = cache.purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get("new")).contains("2");
    }

    @Test
    void clearEmptiesTheCacheButKeepsLifetimeCounters() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");
        cache.get("k");

        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().puts()).isEqualTo(1);
    }

    @Test
    void hitRatioReflectsReads() {
        TtlCache<String, String> cache = cache(10);
        cache.put("k", "v");
        cache.get("k");
        cache.get("k");
        cache.get("absent");

        assertThat(cache.stats().hitRatio()).isEqualTo(2.0 / 3.0);
    }

    @Test
    void hitRatioIsZeroBeforeAnyRead() {
        assertThat(cache(10).stats().hitRatio()).isZero();
    }

    @Test
    void rejectsNonPositiveConfiguration() {
        assertThatThrownBy(() -> new TtlCache<>(Duration.ZERO, 10, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TtlCache<>(TTL, 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
