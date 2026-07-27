package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long>, JpaSpecificationExecutor<Mission> {

    List<Mission> findByUserId(Long userId);

    List<Mission> findByAwardedPilotId(Long awardedPilotId);

    /** Awarded missions whose flight window has ended — the overdue scheduler's candidates. */
    List<Mission> findByAwardedPilotIdIsNotNullAndStatusInAndEndTimeBefore(
            Collection<MissionStatus> statuses, Instant endTime);

    // The open-feed search is built dynamically as a Specification in JpaMissionDataAccess, so only
    // the filters actually supplied become predicates — no null bind parameters reach SQL.

    // Inject data.access.MissionDataAccess, not this interface: JpaMissionDataAccess is the only
    // permitted consumer, so that the caching decorator observes every mission read and write.
}
