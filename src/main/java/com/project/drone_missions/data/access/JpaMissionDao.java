package com.project.drone_missions.data.access;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionModeration;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.MissionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The database-backed {@link MissionDao}. This is the only class in the application
 * permitted to reference {@link MissionRepository}.
 *
 * <p>It holds no cache: {@link #findById} and {@link #findFresh} are the same query here, and
 * the two only diverge once a caching decorator wraps this class.
 */
@Repository
@AllArgsConstructor
public class JpaMissionDao implements MissionDao {

    private final MissionRepository repository;

    @Override
    public Optional<Mission> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Mission> findFresh(Long id) {
        return repository.findById(id);
    }

    /**
     * The open-feed search is built dynamically as a Specification, so only the filters
     * actually supplied become predicates — no null bind parameters reach SQL.
     */
    @Override
    public List<Mission> findOpen(OpenMissionQuery query) {
        Specification<Mission> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(query.statuses()));
            // Moderation: only VISIBLE missions from unsuspended designers reach the
            // feed. Constant predicates, so OpenMissionQuery cache keys stay valid;
            // legacy ownerless rows (null designer) stay visible.
            predicates.add(cb.equal(root.get("moderation"), MissionModeration.VISIBLE));
            predicates.add(cb.or(
                    cb.isNull(root.get("designer")),
                    cb.isFalse(root.get("designer").get("suspended"))));
            if (query.location() != null) {
                predicates.add(cb.like(cb.lower(root.<String>get("location")),
                        "%" + query.location().toLowerCase() + "%"));
            }
            if (query.keyword() != null) {
                String pattern = "%" + query.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), pattern),
                        cb.like(cb.lower(root.<String>get("name")), pattern)));
            }
            if (query.from() != null) {
                predicates.add(cb.and(
                        cb.lessThan(root.<Instant>get("startTime"), query.to()),
                        cb.greaterThanOrEqualTo(root.<Instant>get("endTime"), query.from())));
            }
            criteriaQuery.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
    }

    @Override
    public List<Mission> findByUserId(Long userId) {
        return repository.findByDesigner_Id(userId);
    }

    @Override
    public List<Mission> findByAwardedPilotId(Long pilotId) {
        return repository.findByAwardedPilot_Id(pilotId);
    }

    @Override
    public List<Mission> findOverdue(Collection<MissionStatus> statuses, Instant endedBefore) {
        return repository.findByAwardedPilot_IdIsNotNullAndStatusInAndEndTimeBefore(
                statuses, endedBefore);
    }

    /** No cache here, so nothing to drop. */
    @Override
    public void invalidateLists() {
    }

    @Override
    public Page<Mission> searchAll(String pattern, Pageable pageable) {
        return repository.searchAll(pattern, pageable);
    }

    @Override
    public Map<MissionStatus, Long> countByStatus() {
        return repository.countByStatus().stream()
                .collect(Collectors.toMap(
                        MissionRepository.StatusCount::getStatus,
                        MissionRepository.StatusCount::getTotal));
    }

    @Override
    public Mission save(Mission mission) {
        return repository.save(mission);
    }

    @Override
    public void delete(Mission mission) {
        repository.delete(mission);
    }
}
