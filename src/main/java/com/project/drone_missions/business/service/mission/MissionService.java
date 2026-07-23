package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionAccessDeniedException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.MissionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class MissionService {

    /**
     * The statuses that make a mission part of the open marketplace — work actually on
     * offer, visible to everyone. DRAFT is excluded because the designer is still planning
     * it. AWARDED and later belong to one chosen pilot, and no mission records who that is
     * yet, so they stay hidden from everyone rather than shown to all.
     */
    private static final Set<MissionStatus> OPEN_STATUSES =
            Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING);

    private final MissionRepository repository;

    public Mission create(Mission mission) {
        return repository.save(mission);
    }

    /**
     * The open marketplace: every mission currently available for work, visible to all,
     * narrowed by the optional feed filters. Blank location/keyword and a null date are
     * treated as "not filtering". The date selects missions flyable on that day — i.e.
     * whose flight window overlaps it. Day bounds are computed in the server's local zone
     * so the filter matches the dates as they were entered and are displayed (the client
     * stores/shows flight windows in local time); a fixed UTC boundary would be off by the
     * timezone offset. Assumes the app runs in a single timezone.
     */
    public List<Mission> findOpen(String location, String keyword, LocalDate date) {
        String loc = blankToNull(location);
        String kw = blankToNull(keyword);
        ZoneId zone = ZoneId.systemDefault();
        Instant dayStart = date == null ? null : date.atStartOfDay(zone).toInstant();
        Instant dayEndExclusive = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();

        Specification<Mission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(OPEN_STATUSES));
            if (loc != null) {
                predicates.add(cb.like(cb.lower(root.<String>get("location")), "%" + loc.toLowerCase() + "%"));
            }
            if (kw != null) {
                String pattern = "%" + kw.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), pattern),
                        cb.like(cb.lower(root.<String>get("name")), pattern)));
            }
            if (dayStart != null) {
                predicates.add(cb.and(
                        cb.lessThan(root.<Instant>get("startTime"), dayEndExclusive),
                        cb.greaterThanOrEqualTo(root.<Instant>get("endTime"), dayStart)));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
    }

    /** Treat a null/blank filter value as "not provided" so the query skips it. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The missions the caller created and owns. */
    public List<Mission> findOwnedBy(Long currentUserId) {
        return repository.findByUserId(currentUserId);
    }

    /**
     * @throws MissionNotFoundException if no such mission exists <em>or</em> the caller
     *         may not see it — a mission a caller cannot read must not be distinguishable
     *         from one that does not exist, or the 404 vs 403 itself would confirm a
     *         draft's existence.
     */
    public Mission findById(Long id, Long currentUserId) {
        Mission mission = getOrThrow(id);

       // Long currentUserId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        if (!isVisibleTo(mission, currentUserId)) {
            throw new MissionNotFoundException(id);
        }
        return mission;
    }

    public Mission update(Long id, Mission changes, Long currentUserId) {
        Mission mission = getOrThrow(id);

        requireOwner(mission, currentUserId);
        mission.setName(changes.getName());
        mission.setDescription(changes.getDescription());
        mission.setStartTime(changes.getStartTime());
        mission.setEndTime(changes.getEndTime());
        mission.setLocation(changes.getLocation());
        mission.setBiddingDeadline(changes.getBiddingDeadline());
        mission.setWaypoints(changes.getWaypoints());
        mission.setGeofence(changes.getGeofence());
        // status is intentionally not modified on update — a mission's
        // lifecycle status is never changed by an edit.
        return repository.save(mission);
    }

    public void delete(Long id, Long currentUserId) {
        //Long currentUserId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Mission mission = getOrThrow(id);
        requireOwner(mission, currentUserId);
        repository.delete(mission);
    }

    private Mission getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MissionNotFoundException(id));
    }

    /** Visible to its owner, or to anyone once it is open for work. */
    private boolean isVisibleTo(Mission mission, Long currentUserId) {
        return currentUserId.equals(mission.getUserId())
                || OPEN_STATUSES.contains(mission.getStatus());
    }

    /** Only the mission's creator may modify or delete it. */
    private void requireOwner(Mission mission, Long currentUserId) {
        if (!currentUserId.equals(mission.getUserId())) {
            throw new MissionAccessDeniedException(mission.getId());
        }
    }
}
