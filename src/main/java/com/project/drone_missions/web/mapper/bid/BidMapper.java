package com.project.drone_missions.web.mapper.bid;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.MissionRepository;
import com.project.drone_missions.data.repository.UserRepository;
import com.project.drone_missions.web.dto.bid.BidResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Enriches bids with the mission and pilot display names (per-bid lookups —
 * fine at this scale; revisit with a join if bid lists ever grow large).
 */
@Component
@AllArgsConstructor
public class BidMapper {

    private final MissionRepository missionRepository;
    private final UserRepository userRepository;

    public BidResponse toResponse(Bid bid) {
        String missionName = missionRepository.findById(bid.getMissionId())
                .map(Mission::getName)
                .orElse(null);
        String pilotName = userRepository.findById(bid.getPilotId())
                .map(User::getUsername)
                .orElse(null);
        return new BidResponse(
                bid.getId(),
                bid.getMissionId(),
                missionName,
                bid.getPilotId(),
                pilotName,
                bid.getAmount(),
                bid.getMessage(),
                bid.getStatus(),
                bid.getCreatedAt(),
                bid.getUpdatedAt()
        );
    }
}
