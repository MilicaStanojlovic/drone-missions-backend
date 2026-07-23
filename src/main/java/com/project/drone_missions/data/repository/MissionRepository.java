package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long>, JpaSpecificationExecutor<Mission> {

    List<Mission> findByUserId(Long userId);

    List<Mission> findByAwardedPilotId(Long awardedPilotId);

    // The open-feed search is built dynamically as a Specification in MissionService, so only
    // the filters actually supplied become predicates — no null bind parameters reach SQL.
}
