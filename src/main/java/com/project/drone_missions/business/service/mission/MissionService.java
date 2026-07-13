package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionAccessDeniedException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.repository.MissionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MissionService {

    private final MissionRepository repository;

    public Mission create(Mission mission) {
        return repository.save(mission);
    }

    public List<Mission> findAll() {
        return repository.findAll();
    }

    public List<Mission> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public Mission findById(Long id) {
        return getOrThrow(id);
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

    /** Only the mission's creator may modify or delete it. */
    private void requireOwner(Mission mission, Long currentUserId) {
        if (!currentUserId.equals(mission.getUserId())) {
            throw new MissionAccessDeniedException(mission.getId());
        }
    }
}
