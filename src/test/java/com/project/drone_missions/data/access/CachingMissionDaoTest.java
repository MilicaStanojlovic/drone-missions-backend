package com.project.drone_missions.data.access;

import com.project.drone_missions.config.MissionCacheProperties;
import com.project.drone_missions.data.model.GeoPoint;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionModeration;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito over the {@link MissionDao} <em>interface</em> — no Spring context and no
 * database. Being able to write this at all is the testability argument for introducing the
 * layer, so the delegate is deliberately mocked as the interface rather than a repository.
 */
@ExtendWith(MockitoExtension.class)
class CachingMissionDaoTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final MissionCacheProperties PROPS =
            new MissionCacheProperties(true, Duration.ofMinutes(5), 100, 50);

    @Mock
    private MissionDao delegate;

    private CachingMissionDao cache;

    @BeforeEach
    void setUp() {
        cache = new CachingMissionDao(delegate, PROPS, CLOCK);
    }

    @AfterEach
    void clearAnyTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Mission mission(Long id) {
        return new Mission(id, "Survey", "desc", MissionStatus.PUBLISHED, MissionModeration.VISIBLE,
                user(7L), null,
                Instant.parse("2026-02-01T10:00:00Z"), Instant.parse("2026-02-01T12:00:00Z"),
                "Novi Sad", null,
                new ArrayList<>(List.of(new GeoPoint(45.0, 19.0))), null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
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
    void cachedReadNeverHandsBackTheStoredInstance() {
        Mission stored = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(stored));

        Mission first = cache.findById(1L).orElseThrow();
        Mission second = cache.findById(1L).orElseThrow();
        Mission third = cache.findById(1L).orElseThrow();

        assertThat(second).isNotSameAs(third);
        assertThat(second).isNotSameAs(stored);
        assertThat(first).isNotSameAs(second);
        assertThat(second.getName()).isEqualTo("Survey");
        assertThat(second.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
    }

    @Test
    void mutatingAReturnedMissionDoesNotCorruptTheCache() {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));
        cache.findById(1L);

        Mission borrowed = cache.findById(1L).orElseThrow();
        borrowed.setName("tampered");
        borrowed.setStatus(MissionStatus.CANCELLED);

        Mission fresh = cache.findById(1L).orElseThrow();
        assertThat(fresh.getName()).isEqualTo("Survey");
        assertThat(fresh.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
    }

    @Test
    void mutatingTheInstanceTheDelegateReturnedDoesNotCorruptTheCache() {
        Mission fromDb = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(fromDb));
        cache.findById(1L);

        fromDb.setName("changed after caching");

        assertThat(cache.findById(1L).orElseThrow().getName()).isEqualTo("Survey");
    }

    @Test
    void cachedWaypointsAreImmutable() {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));
        cache.findById(1L);

        Mission cached = cache.findById(1L).orElseThrow();

        assertThatThrownBy(() -> cached.getWaypoints().add(new GeoPoint(1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingMissionsAreNotCached() {
        when(delegate.findById(99L)).thenReturn(Optional.empty());

        cache.findById(99L);
        cache.findById(99L);

        verify(delegate, times(2)).findById(99L);
    }

    // ---- invalidation ----

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

    @Test
    void findFreshEvictsAndNeverPopulates() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.findFresh(1L)).thenReturn(Optional.of(m));
        cache.findById(1L);

        cache.findFresh(1L);
        cache.findById(1L);

        verify(delegate, times(2)).findById(1L);
    }

    // ---- list caching ----

    @Test
    void repeatedFeedQueryIsServedFromCache() {
        OpenMissionQuery query = new OpenMissionQuery(
                Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING), null, null, null, null);
        when(delegate.findOpen(query)).thenReturn(List.of(mission(1L), mission(2L)));

        List<Mission> first = cache.findOpen(query);
        List<Mission> second = cache.findOpen(query);

        verify(delegate, times(1)).findOpen(query);
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        assertThat(second.get(0).getId()).isEqualTo(1L);
        assertThat(second.get(1).getId()).isEqualTo(2L);
    }

    @Test
    void feedOrderIsPreservedThroughTheCache() {
        OpenMissionQuery query = new OpenMissionQuery(Set.of(MissionStatus.PUBLISHED), null, null, null, null);
        when(delegate.findOpen(query)).thenReturn(List.of(mission(3L), mission(1L), mission(2L)));

        cache.findOpen(query);
        List<Mission> cached = cache.findOpen(query);

        assertThat(cached).extracting(Mission::getId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void myMissionsListIsCachedPerUser() {
        when(delegate.findByUserId(7L)).thenReturn(List.of(mission(1L)));

        cache.findByUserId(7L);
        cache.findByUserId(7L);

        verify(delegate, times(1)).findByUserId(7L);
    }

    @Test
    void aWriteClearsTheListCache() {
        OpenMissionQuery query = new OpenMissionQuery(Set.of(MissionStatus.PUBLISHED), null, null, null, null);
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

    @Test
    void statusCountsAreNotCached() {
        when(delegate.countByStatus()).thenReturn(Map.of(MissionStatus.PUBLISHED, 2L));

        cache.countByStatus();
        cache.countByStatus();

        verify(delegate, times(2)).countByStatus();
    }

    @Test
    void adminSearchIsNotCached() {
        when(delegate.searchAll(any(), any())).thenReturn(Page.empty());

        cache.searchAll(null, PageRequest.of(0, 20));
        cache.searchAll(null, PageRequest.of(0, 20));

        verify(delegate, times(2)).searchAll(any(), any());
    }

    // ---- transaction synchronisation ----

    /** The BidService.place case: a mission write with no surrounding transaction. */
    @Test
    void writeOutsideATransactionEvictsImmediatelyAndDoesNotThrow() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.save(m)).thenReturn(m);
        cache.findById(1L);

        cache.save(m);

        cache.findById(1L);
        verify(delegate, times(2)).findById(1L);
    }

    /** A reader repopulating mid-transaction must not survive the commit. */
    @Test
    void entryRepopulatedDuringATransactionIsClearedOnCompletion() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.save(m)).thenReturn(m);

        TransactionSynchronizationManager.initSynchronization();

        cache.save(m);
        cache.findById(1L);   // a concurrent reader re-caches the pre-commit row

        List<TransactionSynchronization> syncs =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        cache.findById(1L);
        verify(delegate, times(2)).findById(1L);
    }

    @Test
    void evictionAlsoHappensAfterARollback() {
        Mission m = mission(1L);
        when(delegate.findById(1L)).thenReturn(Optional.of(m));
        when(delegate.save(m)).thenReturn(m);

        TransactionSynchronizationManager.initSynchronization();
        cache.save(m);
        cache.findById(1L);

        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        cache.findById(1L);
        verify(delegate, times(2)).findById(1L);
    }

    // ---- concurrency ----

    @Test
    void concurrentReadsOfTheSameMissionAreSafe() throws Exception {
        when(delegate.findById(1L)).thenReturn(Optional.of(mission(1L)));

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < 100; j++) {
                        cache.findById(1L);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }

        // atLeastOnce, not times(1): concurrent misses may double-load by design.
        verify(delegate, atLeastOnce()).findById(1L);
        assertThat(cache.entityStats().size()).isEqualTo(1);
    }

    @Test
    void nullIdIsHandledWithoutTouchingTheDelegate() {
        assertThat(cache.findById(null)).isEmpty();
        assertThat(cache.findFresh(null)).isEmpty();

        verify(delegate, never()).findById(anyLong());
        verify(delegate, never()).findFresh(anyLong());
    }
}
