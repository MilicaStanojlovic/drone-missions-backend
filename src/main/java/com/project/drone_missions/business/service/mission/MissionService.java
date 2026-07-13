package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.repository.MissionRepository;
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

    public Mission findById(Long id) {
        return getOrThrow(id);
    }

    public Mission update(Long id, Mission changes) {
        Mission mission = getOrThrow(id);
        mission.setName(changes.getName());
        mission.setDescription(changes.getDescription());
        mission.setStartTime(changes.getStartTime());
        mission.setEndTime(changes.getEndTime());
        // status is intentionally not modified on update — a mission's
        // lifecycle status is never changed by an edit.
        return repository.save(mission);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new MissionNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private Mission getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MissionNotFoundException(id));
    }
}
