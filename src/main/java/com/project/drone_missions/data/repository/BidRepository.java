package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByMissionIdAndPilotId(Long missionId, Long pilotId);

    List<Bid> findByMissionIdOrderByCreatedAtDesc(Long missionId);

    List<Bid> findByPilotIdOrderByCreatedAtDesc(Long pilotId);

    List<Bid> findByMissionIdAndStatus(Long missionId, BidStatus status);
}
