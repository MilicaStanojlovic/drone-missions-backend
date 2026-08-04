package com.project.drone_missions.web.controller.stats;

import com.project.drone_missions.business.service.stats.PlatformStatsService;
import com.project.drone_missions.web.dto.stats.PlatformStatsResponse;
import com.project.drone_missions.web.mapper.stats.PlatformStatsMapper;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/platform-stats")
public class PlatformStatsController {

    private final PlatformStatsService service;
    private final PlatformStatsMapper mapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatformStatsResponse> overview() {
        return ResponseEntity.ok(mapper.toResponse(service.overview()));
    }
}
