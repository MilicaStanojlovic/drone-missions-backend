package com.project.drone_missions.data.access;

import com.project.drone_missions.config.SpringCacheConfig;
import com.project.drone_missions.data.model.GeoPoint;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @Cacheable} is applied by a proxy, so unlike {@link CachingMissionDataAccessTest} this
 * cannot be a plain Mockito test — without a Spring context the annotations would do nothing and
 * every assertion would pass for the wrong reason.
 *
 * <p>It is still not a {@code @SpringBootTest}: this loads only {@link SpringCacheConfig} plus a
 * mocked delegate, so there is no database, no Flyway and no web layer, and it runs in
 * milliseconds. Loading the real configuration rather than a hand-rolled equivalent means the
 * {@code CaffeineCacheManager} wiring, the property binding and the profile gate are covered too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {SpringCacheConfig.class, SpringCacheMissionDataAccessTest.MockDelegate.class})
@ActiveProfiles("cache-spring")
@TestPropertySource(properties = {
        "app.cache.mission.enabled=true",
        "app.cache.mission.ttl=5m",
        "app.cache.mission.max-size=100",
        "app.cache.mission.list-max-size=50",
        "app.cache.mission.report-interval=PT5M"
})
class SpringCacheMissionDataAccessTest {

    /** The database-backed end of the decorator, mocked as the concrete type the config injects. */
    @Configuration
    static class MockDelegate {
        @Bean
        JpaMissionDataAccess jpaMissionDataAccess() {
            return Mockito.mock(JpaMissionDataAccess.class);
        }
    }

    @Autowired
    private SpringCacheMissionDataAccess cache;

    @Autowired
    private JpaMissionDataAccess delegate;

    @Autowired
    private CacheManager cacheManager;

    /** The context is shared across methods, so both the mock and the caches need resetting. */
    @BeforeEach
    void reset() {
        Mockito.reset(delegate);
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Mission mission(Long id) {
        return new Mission(id, "Survey", "desc", MissionStatus.PUBLISHED,
                user(7L), null,
                Instant.parse("2026-02-01T10:00:00Z"), Instant.parse("2026-02-01T12:00:00Z"),
                "Novi Sad", null,
                new ArrayList<>(List.of(new GeoPoint(45.0, 19.0))), null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static OpenMissionQuery openQuery(String location) {
        return new OpenMissionQuery(
                Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING), location, null, null, null);
    }

    // ---- by-id caching ----

    @Test
    void secondReadIsServedFromCache() {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));

        cache.findById(1L);
        cache.findById(1L);

        verify(delegate, times(1)).findById(1L);
    }

    @Test
    void differentIdsAreCachedSeparately() {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));
        when(delegate.findById(2L)).thenReturn(Optional.of(mission(2L)));

        cache.findById(1L);
        cache.findById(2L);
        cache.findById(1L);

        verify(delegate, times(1)).findById(1L);
        verify(delegate, times(1)).findById(2L);
    }

    @Test
    void cachedValueRoundTripsIntact() {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));
        cache.findById(1L);

        Mission cached = cache.findById(1L).orElseThrow();

        assertThat(cached.getId()).isEqualTo(1L);
        assertThat(cached.getName()).isEqualTo("Survey");
        assertThat(cached.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
    }

    /**
     * Pins the documented difference from {@link CachingMissionDataAccess}, which copies in and
     * out: here the stored instance itself is handed to every caller. Asserted so the behaviour
     * is a known trade-off rather than a surprise — mutating a returned mission in place would
     * corrupt the entry for everyone. Safe today because every write flow uses
     * {@link MissionDataAccess#findFresh}, which is never served from cache.
     */
    @Test
    void cachedReadHandsBackTheStoredInstance() {
        Mission stored = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(stored));

        Mission first = cache.findById(1L).orElseThrow();
        Mission second = cache.findById(1L).orElseThrow();

        assertThat(first).isSameAs(stored);
        assertThat(second).isSameAs(stored);
    }

    /** The {@code unless = "#result == null"} guard: Spring unwraps the Optional before evaluating it. */
    @Test
    void missingMissionsAreNotCached() {
        when(delegate.findById(99L)).thenReturn(Optional.empty());

        assertThat(cache.findById(99L)).isEmpty();
        assertThat(cache.findById(99L)).isEmpty();

        verify(delegate, times(2)).findById(99L);
    }

    @Test
    void nullIdIsHandledWithoutTouchingTheDelegate() {
        assertThat(cache.findById(null)).isEmpty();
        assertThat(cache.findFresh(null)).isEmpty();

        verify(delegate, never()).findById(anyLong());
        verify(delegate, never()).findFresh(anyLong());
    }

    // ---- invalidation ----

    @Test
    void findFreshEvictsAndNeverPopulates() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.findFresh(1L)).thenReturn(Optional.of(m));
        cache.findById(1L);

        cache.findFresh(1L);
        cache.findById(1L);

        verify(delegate, times(2)).findById(1L);
        verify(delegate, times(1)).findFresh(1L);
    }

    @Test
    void saveEvictsTheMission() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.save(m)).thenReturn(m);
        cache.findById(1L);

        cache.save(m);
        cache.findById(1L);

        verify(delegate, times(2)).findById(1L);
    }

    @Test
    void saveDoesNotPopulateTheCacheFromItsResult() {
        Mission m = mission(1L);
        when(delegate.save(m)).thenReturn(m);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));

        cache.save(m);
        cache.findById(1L);

        verify(delegate, times(1)).findById(1L);
    }

    @Test
    void deleteEvictsTheMission() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        cache.findById(1L);

        cache.delete(m);
        cache.findById(1L);

        verify(delegate, times(2)).findById(1L);
    }

    /**
     * {@code allEntries = true} is coarse by necessity — a write clears the entity rows as well
     * as the lists. This is the cost the hand-written id-list design avoids, and asserting it
     * keeps the comparison honest.
     */
    @Test
    void aWriteAlsoClearsUnrelatedEntities() {
        Mission one = mission(1L);
        Mission two = mission(2L);
        when(delegate.findById(1L)).thenReturn(Optional.of(one));
        when(delegate.findById(2L)).thenReturn(Optional.of(two));
        when(delegate.save(one)).thenReturn(one);
        cache.findById(1L);
        cache.findById(2L);

        cache.save(one);
        cache.findById(2L);

        verify(delegate, times(2)).findById(2L);
    }

    // ---- list caching ----

    @Test
    void repeatedFeedQueryIsServedFromCache() {
        OpenMissionQuery query = openQuery(null);
        when(delegate.findOpen(query)).thenReturn(List.of(mission(1L), mission(2L)));

        List<Mission> first = cache.findOpen(query);
        List<Mission> second = cache.findOpen(query);

        verify(delegate, times(1)).findOpen(query);
        assertThat(first).hasSize(2);
        assertThat(second).extracting(Mission::getId).containsExactly(1L, 2L);
    }

    @Test
    void feedOrderIsPreservedThroughTheCache() {
        OpenMissionQuery query = openQuery(null);
        when(delegate.findOpen(query)).thenReturn(List.of(mission(3L), mission(1L), mission(2L)));

        cache.findOpen(query);
        List<Mission> cached = cache.findOpen(query);

        assertThat(cached).extracting(Mission::getId).containsExactly(3L, 1L, 2L);
    }

    /** Value equality on the query record is what makes it usable as a key at all. */
    @Test
    void differentQueriesAreDistinctKeys() {
        OpenMissionQuery noviSad = openQuery("Novi Sad");
        OpenMissionQuery beograd = openQuery("Beograd");
        when(delegate.findOpen(noviSad)).thenReturn(List.of(mission(1L)));
        when(delegate.findOpen(beograd)).thenReturn(List.of(mission(2L)));

        cache.findOpen(noviSad);
        cache.findOpen(beograd);
        cache.findOpen(noviSad);

        verify(delegate, times(1)).findOpen(noviSad);
        verify(delegate, times(1)).findOpen(beograd);
    }

    @Test
    void myMissionsListIsCachedPerUser() {
        when(delegate.findByUserId(7L)).thenReturn(List.of(mission(1L)));
        when(delegate.findByUserId(8L)).thenReturn(List.of(mission(2L)));

        cache.findByUserId(7L);
        cache.findByUserId(8L);
        cache.findByUserId(7L);

        verify(delegate, times(1)).findByUserId(7L);
        verify(delegate, times(1)).findByUserId(8L);
    }

    /** The owner and pilot lists share one cache, so their key prefixes must not collide. */
    @Test
    void ownerAndPilotListsDoNotShareAKey() {
        when(delegate.findByUserId(7L)).thenReturn(List.of(mission(1L)));
        when(delegate.findByAwardedPilotId(7L)).thenReturn(List.of(mission(2L)));

        List<Mission> owned = cache.findByUserId(7L);
        List<Mission> flown = cache.findByAwardedPilotId(7L);

        assertThat(owned).extracting(Mission::getId).containsExactly(1L);
        assertThat(flown).extracting(Mission::getId).containsExactly(2L);
    }

    @Test
    void aWriteClearsTheListCache() {
        OpenMissionQuery query = openQuery(null);
        Mission m = mission(1L);
        when(delegate.findOpen(query)).thenReturn(List.of(m));
        when(delegate.save(m)).thenReturn(m);
        cache.findOpen(query);

        cache.save(m);
        cache.findOpen(query);

        verify(delegate, times(2)).findOpen(query);
    }

    @Test
    void overdueSweepIsNotCached() {
        when(delegate.findOverdue(any(), any())).thenReturn(List.of(mission(1L)));

        cache.findOverdue(Set.of(MissionStatus.AWARDED), Instant.now());
        cache.findOverdue(Set.of(MissionStatus.AWARDED), Instant.now());

        verify(delegate, times(2)).findOverdue(any(), any());
    }

    // ---- observability ----

    /**
     * recordStats() must be on, or the scheduled report would log zeroes forever.
     *
     * <p>Asserted as a delta because Caffeine's counters are cumulative and survive
     * {@code clear()}, so they carry across test methods sharing this context. {@code TtlCache}
     * behaves the same way — see its "clear keeps the counters" test.
     */
    @Test
    void statisticsAreRecorded() {
        long hitsBefore = nativeStats(SpringCacheMissionDataAccess.MISSIONS).hitCount();
        long missesBefore = nativeStats(SpringCacheMissionDataAccess.MISSIONS).missCount();
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));

        cache.findById(1L);   // miss, then load
        cache.findById(1L);   // hit

        assertThat(nativeStats(SpringCacheMissionDataAccess.MISSIONS).hitCount())
                .isEqualTo(hitsBefore + 1);
        assertThat(nativeStats(SpringCacheMissionDataAccess.MISSIONS).missCount())
                .isEqualTo(missesBefore + 1);
    }

    /** The reported line must stay parseable and comparable with the hand-written cache's. */
    @Test
    void reportedStatisticsKeepTheSharedFormat() {
        assertThat(cache.statsOf(SpringCacheMissionDataAccess.MISSIONS))
                .matches("hits=\\d+ misses=\\d+ ratio=[\\d.,]+ size=\\d+ evictions=\\d+");
    }

    /**
     * An untouched cache must report a 0.00 ratio, as {@link CacheStats} does. Caffeine's own
     * {@code hitRate()} answers 1.0 when nothing has been requested, which would make an idle
     * Spring-profile run look like a perfect cache next to an idle default-profile run.
     */
    @Test
    void anUntouchedCacheReportsAZeroRatio() {
        assertThat(SpringCacheMissionDataAccess.formatStats(0, 0, 0, 0))
                .startsWith("hits=0 misses=0 ratio=0")
                .endsWith("size=0 evictions=0");
    }

    @Test
    void anUnknownCacheReportsUnavailableRatherThanFailing() {
        assertThat(cache.statsOf("no-such-cache")).isEqualTo("unavailable");
    }

    private com.github.benmanes.caffeine.cache.stats.CacheStats nativeStats(String cacheName) {
        CaffeineCache cache = (CaffeineCache) Objects.requireNonNull(cacheManager.getCache(cacheName));
        return cache.getNativeCache().stats();
    }
}
