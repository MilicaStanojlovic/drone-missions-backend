package com.project.drone_missions.web.controller.rating;

import com.project.drone_missions.business.service.rating.RatingService;
import com.project.drone_missions.business.service.rating.RatingSummary;
import com.project.drone_missions.web.dto.rating.RatingRequest;
import com.project.drone_missions.web.dto.rating.RatingResponse;
import com.project.drone_missions.web.dto.rating.UserRatingsResponse;
import com.project.drone_missions.web.mapper.rating.RatingMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/ratings")
public class RatingController {

    private final RatingService service;
    private final RatingMapper mapper;

    /** Rate the other side of a completed mission. No role gate — both sides rate. */
    @PostMapping("/mission/{missionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RatingResponse> rate(@PathVariable Long missionId,
                                               @Valid @RequestBody RatingRequest request,
                                               @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(mapper.toResponse(
                service.create(missionId, userId, request.score(), request.comment())));
    }

    /** Both ratings on a mission, so a participant can see whether they have rated yet. */
    @GetMapping("/mission/{missionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RatingResponse>> forMission(@PathVariable Long missionId,
                                                           @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.forMission(missionId, userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** A user's average, count and comments — one call, since a profile shows all three. */
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserRatingsResponse> forUser(@PathVariable Long userId) {
        RatingSummary summary = service.summaryFor(userId);
        return ResponseEntity.ok(new UserRatingsResponse(
                summary.average(),
                summary.count(),
                service.receivedBy(userId).stream().map(mapper::toResponse).toList()));
    }
}
