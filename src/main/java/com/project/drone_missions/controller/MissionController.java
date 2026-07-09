package com.project.drone_missions.controller;

import com.project.drone_missions.dto.MissionRequest;
import com.project.drone_missions.dto.MissionResponse;
import com.project.drone_missions.service.MissionService;
import jakarta.validation.Valid;
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
@RequestMapping("/missions")
public class MissionController {

    private final MissionService service;

    public MissionController(MissionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MissionResponse create(@Valid @RequestBody MissionRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<MissionResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public MissionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public MissionResponse update(@PathVariable Long id, @Valid @RequestBody MissionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
