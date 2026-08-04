package com.project.drone_missions.web.controller.mission;

import com.project.drone_missions.business.service.mission.MissionService;
import com.project.drone_missions.business.service.rating.RatingService;
import com.project.drone_missions.business.service.rating.RatingSummary;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.web.mapper.mission.MissionMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/missions")
public class MissionController {
    private final MissionService service;
    private final MissionMapper mapper;
    private final RatingService ratingService;

    /** One aggregate query for the whole page, so cards never cost a rating lookup each. */
    private List<MissionResponse> toResponses(List<Mission> missions) {
        Map<Long, RatingSummary> ratings = ratingService.summariesFor( // TODO refactor
                missions.stream()
                        .map(Mission::getDesignerId).toList());
        return missions.stream()
                .map(m -> mapper.toResponse(m, ratingOf(ratings, m.getDesignerId())))
                .toList();
    }

    /**
     * A legacy mission has no owner (mission.user_id is nullable), and the map returned for a
     * page of only such missions is immutable — a null key lookup there throws.
     */
    private static RatingSummary ratingOf(Map<Long, RatingSummary> ratings, Long userId) {
        return userId == null ? RatingSummary.NONE : ratings.getOrDefault(userId, RatingSummary.NONE);
    }

    private MissionResponse toResponse(Mission mission) {
        return mapper.toResponse(mission, ratingService.summaryFor(mission.getDesignerId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<MissionResponse> create(@Valid @RequestBody MissionRequest request,
                                                  @AuthenticationPrincipal Long userId) {
        Mission mission = mapper.toEntity(request);
        Mission created = service.create(mission, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    /** The open marketplace; the admin listing lives at /all. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MissionResponse>> findAll(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(toResponses(service.findOpen(location, keyword, date)));
    }

    /** Every mission regardless of status, paged, optionally narrowed by name/designer. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<MissionResponse>> adminList(
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Mission> page = service.searchAll(q, pageable);
        Map<Long, RatingSummary> ratings = ratingService.summariesFor(
                page.getContent().stream().map(Mission::getDesignerId).toList());
        return ResponseEntity.ok(new PagedModel<>(
                page.map(m -> mapper.toResponse(m, ratingOf(ratings, m.getDesignerId())))));
    }

    @GetMapping("/my-missions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MissionResponse>> findMine(@AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponses(service.findOwnedBy(userId)));
    }

    /** The calling pilot's awarded missions ("jobs"). */
    @GetMapping("/my-jobs")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<List<MissionResponse>> findMyJobs(@AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponses(service.findAwardedTo(userId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MissionResponse> findById(@PathVariable Long id,
                                                    @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.findById(id, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<MissionResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody MissionRequest request,
                                                  @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.update(id, mapper.toEntity(request), userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal long userId) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** The awarded pilot starts the mission (AWARDED → IN_PROGRESS). */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<MissionResponse> start(@PathVariable Long id,
                                                 @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.start(id, userId)));
    }

    /** The awarded pilot marks the mission finished (IN_PROGRESS → COMPLETED). */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<MissionResponse> complete(@PathVariable Long id,
                                                    @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.complete(id, userId)));
    }

    /** The mission's creator cancels it (→ CANCELLED), rejecting any outstanding bids. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<MissionResponse> cancel(@PathVariable Long id,
                                                  @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.cancel(id, userId)));
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MissionResponse> hide(@PathVariable Long id,
                                                @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.hide(id, userId)));
    }

    @PostMapping("/{id}/unhide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MissionResponse> unhide(@PathVariable Long id,
                                                  @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(toResponse(service.unhide(id, userId)));
    }

    /** Permanent delete — 204 because the mission no longer exists to be returned. */
    @PostMapping("/{id}/remove")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable Long id,
                                       @AuthenticationPrincipal long userId) {
        service.remove(id, userId);
        return ResponseEntity.noContent().build();
    }
}
