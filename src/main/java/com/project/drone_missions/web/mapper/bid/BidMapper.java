package com.project.drone_missions.web.mapper.bid;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.web.dto.bid.BidResponse;
import org.springframework.stereotype.Component;

/**
 * No repositories: the mission and pilot names come off the relations, so the per-bid
 * lookups this used to do are gone.
 */
@Component
public class BidMapper {

    public BidResponse toResponse(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getMission().getId(),
                bid.getMission().getName(),
                bid.getPilot().getId(),
                bid.getPilot().getUsername(),
                bid.getAmount(),
                bid.getMessage(),
                bid.getStatus(),
                bid.getCreatedAt(),
                bid.getUpdatedAt()
        );
    }
}
