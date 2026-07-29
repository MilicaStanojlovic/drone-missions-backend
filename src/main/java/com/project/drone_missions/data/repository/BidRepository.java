package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByMission_IdAndPilot_Id(Long missionId, Long pilotId);

    List<Bid> findByMission_IdOrderByCreatedAtDesc(Long missionId);

    List<Bid> findByPilot_IdOrderByCreatedAtDesc(Long pilotId);

    List<Bid> findByMission_IdAndStatus(Long missionId, BidStatus status);
}
