package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByMission_IdAndPilot_Id(Long missionId, Long pilotId);

    List<Bid> findByMission_IdOrderByCreatedAtDesc(Long missionId);

    List<Bid> findByPilot_IdOrderByCreatedAtDesc(Long pilotId);

    List<Bid> findByMission_IdAndStatus(Long missionId, BidStatus status);

    /** Any status counts — withdrawn bids are deleted rows, so this is live bids only. */
    @Query("select count(b) as count, coalesce(sum(b.amount), 0) as totalAmount from Bid b")
    BidVolume volume();

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface BidVolume {
        long getCount();

        BigDecimal getTotalAmount();
    }
}
