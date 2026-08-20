package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long>, JpaSpecificationExecutor<Mission> {

    // HIDDEN only affects the open feed, so the owner and awarded-pilot lists
    // need no moderation filter; admin removal is a real delete, not a state.
    List<Mission> findByDesigner_Id(Long userId);

    List<Mission> findByAwardedPilot_Id(Long awardedPilotId);

    /** Awarded missions whose flight window has ended — the overdue scheduler's candidates. */
    List<Mission> findByAwardedPilot_IdIsNotNullAndStatusInAndEndTimeBefore(
            Collection<MissionStatus> statuses, Instant endTime);

    @Query("select m.status as status, count(m) as total from Mission m group by m.status")
    List<StatusCount> countByStatus();

    /**
     * The admin listing. {@code pattern} arrives as a ready lowercase %…% LIKE
     * pattern or null (see AuditLogRepository for why no function wraps it).
     * Explicit left join: navigating m.designer.username would inner-join and
     * silently drop legacy ownerless missions from the admin list.
     */
    @Query("""
            select m from Mission m left join m.designer d
            where (:pattern is null
                   or lower(m.name) like :pattern
                   or lower(d.username) like :pattern)
            """)
    Page<Mission> searchAll(@Param("pattern") String pattern, Pageable pageable);

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
