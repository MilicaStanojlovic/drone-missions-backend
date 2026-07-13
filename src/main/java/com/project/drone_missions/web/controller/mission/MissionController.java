package com.project.drone_missions.web.controller.mission;

import com.project.drone_missions.business.service.mission.MissionService;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.security.CurrentUserId;
import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.web.mapper.mission.MissionMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/missions")
public class MissionController {
    private final MissionService service;
    private final MissionMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MissionResponse create(@Valid @RequestBody MissionRequest request,
                                  @CurrentUserId Long userId) {
        Mission mission = mapper.toEntity(request);
        mission.setUserId(userId);
        return mapper.toResponse(service.create(mission));
    }

    @GetMapping
    public List<MissionResponse> findAll() {
        return service.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/my-missions")
    public List<MissionResponse> findMine(@CurrentUserId Long userId) {
        return service.findByUserId(userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public MissionResponse findById(@PathVariable Long id) {
        return mapper.toResponse(service.findById(id));
    }

    @PutMapping("/{id}")
    public MissionResponse update(@PathVariable Long id,
                                  @Valid @RequestBody MissionRequest request,
                                  @CurrentUserId Long userId) {
        return mapper.toResponse(service.update(id, mapper.toEntity(request), userId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @CurrentUserId Long userId) {
        service.delete(id, userId);
    }
}
