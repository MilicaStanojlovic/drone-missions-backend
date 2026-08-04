package com.project.drone_missions.web.mapper.audit;

import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.web.dto.audit.AuditLogResponse;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActor() == null ? null : log.getActor().getUsername(),
                log.getActorRole(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
