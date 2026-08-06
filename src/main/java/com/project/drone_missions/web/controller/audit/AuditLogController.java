package com.project.drone_missions.web.controller.audit;

import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.web.dto.audit.AuditLogResponse;
import com.project.drone_missions.web.mapper.audit.AuditLogMapper;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    private final AuditService service;
    private final AuditLogMapper mapper;

    /** PagedModel, not Page: the stable JSON page envelope since Spring Data Commons 3.3. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<AuditLogResponse>> list( // parametre TODO
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(
                service.search(actorId, action, role, q, pageable).map(mapper::toResponse)));
    }
}
