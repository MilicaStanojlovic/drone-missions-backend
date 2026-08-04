package com.project.drone_missions.business.service.stats;

import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class PlatformStatsService {

    /** The overview's bids-per-mission chart shows at most this many bars. */
    private static final int TOP_MISSIONS = 6;

    private final MissionDao missionDao;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;

    /**
     * The repository aggregates are sparse; every status and role is presented,
     * zero-filled, so the overview never has to guess whether a missing key
     * means zero or an error.
     */
    public PlatformStats overview() {
        Map<MissionStatus, Long> byStatus = new EnumMap<>(MissionStatus.class);
        for (MissionStatus status : MissionStatus.values()) {
            byStatus.put(status, 0L);
        }
        byStatus.putAll(missionDao.countByStatus());

        Map<UserRole, Long> byRole = new EnumMap<>(UserRole.class);
        for (UserRole role : UserRole.values()) {
            byRole.put(role, 0L);
        }
        userRepository.countByRole().forEach(row -> byRole.put(row.getRole(), row.getTotal()));

        List<PlatformStats.TopMission> topMissions = bidRepository
                .topMissionsByBids(PageRequest.of(0, TOP_MISSIONS)).stream()
                .map(row -> new PlatformStats.TopMission(row.getName(), row.getTotal()))
                .toList();

        BidRepository.BidVolume bids = bidRepository.volume();
        return new PlatformStats(
                byStatus,
                userRepository.countByRoleAndSuspendedFalse(UserRole.PILOT),
                bids.getCount(),
                bids.getTotalAmount(),
                userRepository.countBySuspendedTrue(),
                byRole,
                topMissions);
    }
}
