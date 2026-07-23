package com.project.drone_missions.web.controller.mission;

import com.project.drone_missions.business.service.mission.MissionService;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.security.UserPrincipal;
import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.web.mapper.mission.MissionMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/missions")
public class MissionController {
    private final MissionService service;
    private final MissionMapper mapper;

    @PostMapping
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<MissionResponse> create(@Valid @RequestBody MissionRequest request,
                                                  @AuthenticationPrincipal Long userId) {
        Mission mission = mapper.toEntity(request);
        mission.setUserId(userId);
        Mission created = service.create(mission);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MissionResponse>> findAll(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findOpen(location, keyword, date).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @GetMapping("/my-missions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MissionResponse>> findMine(@AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(service.findOwnedBy(userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** The calling pilot's awarded missions ("jobs"). */
    @GetMapping("/my-jobs")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<List<MissionResponse>> findMyJobs(@AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(service.findAwardedTo(userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MissionResponse> findById(@PathVariable Long id,
                                                    @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(id, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<MissionResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody MissionRequest request,
                                                  @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toEntity(request), userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal long userId) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** The awarded pilot marks the mission finished (IN_PROGRESS → COMPLETED). */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<MissionResponse> complete(@PathVariable Long id,
                                                    @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(service.complete(id, userId)));
    }
}
