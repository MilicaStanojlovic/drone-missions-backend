package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByMission_IdAndPilot_Id(Long missionId, Long pilotId);

    List<Bid> findByMission_IdOrderByCreatedAtDesc(Long missionId);

    List<Bid> findByPilot_IdOrderByCreatedAtDesc(Long pilotId);

    List<Bid> findByMission_IdAndStatus(Long missionId, BidStatus status);

    /** One statement to decide every other pending bid, instead of a select already done + a save per bid. */
    @Modifying
    @Transactional
    @Query("update Bid b set b.status = :newStatus where b.mission.id = :missionId and b.status = :oldStatus and b.id <> :exceptBidId")
    int updateStatusForOtherBids(@Param("missionId") Long missionId, @Param("exceptBidId") Long exceptBidId,
                                  @Param("oldStatus") BidStatus oldStatus, @Param("newStatus") BidStatus newStatus);
}
