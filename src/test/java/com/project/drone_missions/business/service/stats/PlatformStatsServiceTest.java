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

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(stats.activePilots()).isZero();
        assertThat(stats.bidCount()).isZero();
        assertThat(stats.bidAmountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void activePilotsCountsExactlyTheUnsuspendedPilotRole() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(userRepository.countByRoleAndSuspendedAtIsNull(UserRole.PILOT)).thenReturn(7L);

        assertThat(service.overview().activePilots()).isEqualTo(7L);
        verify(userRepository).countByRoleAndSuspendedAtIsNull(UserRole.PILOT);
    }

    @Test
    void bidVolumeLandsInTheRightComponents() {
        when(missionDao.countByStatus()).thenReturn(Map.of());
        when(bidRepository.volume()).thenReturn(volume(57L, new BigDecimal("12345.50")));

        PlatformStats stats = service.overview();

        assertThat(stats.bidCount()).isEqualTo(57L);
        assertThat(stats.bidAmountTotal()).isEqualByComparingTo("12345.50");
    }
}
