package com.project.drone_missions.business.service.stats;

import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class PlatformStatsService {

    private final MissionDao missionDao;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;

    /**
     * The DAO's map is sparse; every status is presented, zero-filled, so the
     * overview never has to guess whether a missing key means zero or an error.
     */
    public PlatformStats overview() {
        Map<MissionStatus, Long> byStatus = new EnumMap<>(MissionStatus.class);
        for (MissionStatus status : MissionStatus.values()) {
            byStatus.put(status, 0L);
        }
        byStatus.putAll(missionDao.countByStatus());

        BidRepository.BidVolume bids = bidRepository.volume();
        return new PlatformStats(
                byStatus,
                userRepository.countByRoleAndSuspendedAtIsNull(UserRole.PILOT),
                bids.getCount(),
                bids.getTotalAmount());
    }
}
