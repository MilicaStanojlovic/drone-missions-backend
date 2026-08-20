package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> { // TODO nov interfejs

    Optional<Bid> findByMission_IdAndPilot_Id(Long missionId, Long pilotId);

    List<Bid> findByMission_IdOrderByCreatedAtDesc(Long missionId);

    List<Bid> findByPilot_IdOrderByCreatedAtDesc(Long pilotId);

    List<Bid> findByMission_IdAndStatus(Long missionId, BidStatus status);

    /** Any status counts — withdrawn bids are deleted rows, so this is live bids only. */
    @Query("select count(b) as count, coalesce(sum(b.amount), 0) as totalAmount from Bid b")
    BidVolume volume();

    /**
     * Most-bid-on missions, for the overview's bar chart — pass a page to cap it.
     * Zero-bid missions are naturally absent; no moderation filter (admins see
     * everything, matching the status counts).
     */
    @Query("""
            select b.mission.name as name, count(b) as total from Bid b
            group by b.mission.id, b.mission.name
            order by count(b) desc
            """)
    List<MissionBidCount> topMissionsByBids(Pageable pageable);

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface MissionBidCount {
        String getName();

        Long getTotal();
    }

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface BidVolume {
        long getCount();

        BigDecimal getTotalAmount();
    }
}
