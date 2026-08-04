package com.project.drone_missions.business.service.stats;

import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformStatsServiceTest {

    @Mock
    private MissionDao missionDao;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BidRepository bidRepository;

    private PlatformStatsService service;

    @BeforeEach
    void setUp() {
        service = new PlatformStatsService(missionDao, userRepository, bidRepository);
        when(bidRepository.volume()).thenReturn(volume(0L, BigDecimal.ZERO));
        when(bidRepository.topMissionsByBids(any())).thenReturn(List.of());
        when(userRepository.countByRole()).thenReturn(List.of());
    }

    private static BidRepository.BidVolume volume(long count, BigDecimal total) {
        return new BidRepository.BidVolume() {
            @Override
            public long getCount() {
                return count;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return total;
            }
        };
    }

    private static UserRepository.RoleCount roleCount(UserRole role, long total) {
        return new UserRepository.RoleCount() {
            @Override
            public UserRole getRole() {
                return role;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private static BidRepository.MissionBidCount missionBids(String name, long total) {
        return new BidRepository.MissionBidCount() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    @Test
    void sparseStatusCountsAreZeroFilledToAllStatuses() {
        when(missionDao.countByStatus()).thenReturn(Map.of(MissionStatus.PUBLISHED, 2L));

        Map<MissionStatus, Long> byStatus = service.overview().missionsByStatus();

        assertThat(byStatus).containsOnlyKeys(MissionStatus.values());
        assertThat(byStatus.get(MissionStatus.PUBLISHED)).isEqualTo(2L);
        assertThat(byStatus.get(MissionStatus.DRAFT)).isZero();
        assertThat(byStatus.get(MissionStatus.CANCELLED)).isZero();
    }

    @Test
    void anEmptyPlatformReportsAllZeros() {
        when(missionDao.countByStatus()).thenReturn(Map.of());

        PlatformStats stats = service.overview();

        assertThat(stats.missionsByStatus().values()).containsOnly(0L);
        assertThat(stats.usersByRole()).containsOnlyKeys(UserRole.values());
        assertThat(stats.usersByRole().values()).containsOnly(0L);
        assertThat(stats.activePilots()).isZero();
        assertThat(stats.suspendedUsers()).isZero();
        assertThat(stats.bidCount()).isZero();
        assertThat(stats.bidAmountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.topMissionsByBids()).isEmpty();
    }

    @Test
    void activePilotsCountsExactlyTheUnsuspendedPilotRole() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(userRepository.countByRoleAndSuspendedFalse(UserRole.PILOT)).thenReturn(7L);

        assertThat(service.overview().activePilots()).isEqualTo(7L);
        verify(userRepository).countByRoleAndSuspendedFalse(UserRole.PILOT);
    }

    @Test
    void bidVolumeLandsInTheRightComponents() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(bidRepository.volume()).thenReturn(volume(57L, new BigDecimal("12345.50")));

        PlatformStats stats = service.overview();

        assertThat(stats.bidCount()).isEqualTo(57L);
        assertThat(stats.bidAmountTotal()).isEqualByComparingTo("12345.50");
    }

    @Test
    void sparseRoleCountsAreZeroFilledToAllRoles() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(userRepository.countByRole()).thenReturn(List.of(roleCount(UserRole.PILOT, 31L)));
        when(userRepository.countBySuspendedTrue()).thenReturn(3L);

        PlatformStats stats = service.overview();

        assertThat(stats.usersByRole())
                .containsEntry(UserRole.PILOT, 31L)
                .containsEntry(UserRole.DESIGNER, 0L)
                .containsEntry(UserRole.ADMIN, 0L);
        assertThat(stats.suspendedUsers()).isEqualTo(3L);
    }

    @Test
    void topMissionsAreCappedAtSixAndKeepTheirOrder() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(bidRepository.topMissionsByBids(PageRequest.of(0, 6)))
                .thenReturn(List.of(missionBids("Orchard survey", 9L), missionBids("Roof scan", 4L)));

        List<PlatformStats.TopMission> top = service.overview().topMissionsByBids();

        verify(bidRepository).topMissionsByBids(PageRequest.of(0, 6));
        assertThat(top).containsExactly(
                new PlatformStats.TopMission("Orchard survey", 9L),
                new PlatformStats.TopMission("Roof scan", 4L));
    }
}
