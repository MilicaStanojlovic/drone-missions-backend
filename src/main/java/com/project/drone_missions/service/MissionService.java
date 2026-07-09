package com.project.drone_missions.service;

import com.project.drone_missions.dto.MissionRequest;
import com.project.drone_missions.dto.MissionResponse;
import com.project.drone_missions.exception.NotFoundException;
import com.project.drone_missions.mapper.MissionMapper;
import com.project.drone_missions.model.Mission;
import com.project.drone_missions.repository.MissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MissionService {

    private final MissionRepository repository;
    private final MissionMapper mapper;

    public MissionService(MissionRepository repository, MissionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public MissionResponse create(MissionRequest request) {
        Mission mission = mapper.toEntity(request);
        return mapper.toResponse(repository.save(mission));
    }

    public List<MissionResponse> findAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    public MissionResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    public MissionResponse update(Long id, MissionRequest request) {
        Mission mission = getOrThrow(id);
        mapper.apply(request, mission);
        return mapper.toResponse(repository.save(mission));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Mission %d not found".formatted(id));
        }
        repository.deleteById(id);
    }

    private Mission getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mission %d not found".formatted(id)));
    }
}
