package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionAccessDeniedException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.MissionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

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

    /** The open marketplace: every mission currently available for work, visible to all. */
    public List<Mission> findOpen() {
        return repository.findByStatusIn(OPEN_STATUSES);
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
        // status is intentionally not modified on update — a mission's
        // lifecycle status is never changed by an edit.
        return repository.save(mission);
    }

    public void delete(Long id, Long currentUserId) {
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
