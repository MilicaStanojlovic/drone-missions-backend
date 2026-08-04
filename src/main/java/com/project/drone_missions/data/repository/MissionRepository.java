package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionModeration;
import com.project.drone_missions.data.model.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long>, JpaSpecificationExecutor<Mission> {

    // REMOVED missions are withdrawn from the platform entirely, so the owner and
    // awarded-pilot lists exclude them; HIDDEN only affects the open feed.
    List<Mission> findByDesigner_IdAndModerationNot(Long userId, MissionModeration moderation);

    List<Mission> findByAwardedPilot_IdAndModerationNot(Long awardedPilotId, MissionModeration moderation);

    /** Awarded missions whose flight window has ended — the overdue scheduler's candidates. */
    List<Mission> findByAwardedPilot_IdIsNotNullAndStatusInAndEndTimeBeforeAndModerationNot(
            Collection<MissionStatus> statuses, Instant endTime, MissionModeration moderation);

    @Query("select m.status as status, count(m) as total from Mission m group by m.status")
    List<StatusCount> countByStatus();

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface StatusCount {
        MissionStatus getStatus();

        Long getTotal();
    }

    // The open-feed search is built dynamically as a Specification in JpaMissionDao, so only
    // the filters actually supplied become predicates — no null bind parameters reach SQL.

    // Inject data.access.MissionDao, not this interface: JpaMissionDao is the only
    // permitted consumer, so that the caching decorator observes every mission read and write.
}
