package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.data.repository.AuditLogRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records audit-log rows. Callers invoke {@code record} as the last statement
 * after their domain saves: a failed operation never logs, and a failed insert
 * propagates — an audit trail that can be silently skipped is not one.
 */
@Service
@AllArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public AuditLog record(NewAuditEntry entry) {
        AuditLog log = new AuditLog();
        log.setActor(userRepository.getReferenceById(entry.actorId()));
        log.setActorRole(entry.actorRole());
        log.setAction(entry.action());
        log.setTargetType(entry.targetType());
        log.setTargetId(entry.targetId());
        log.setDetails(entry.details());
        return repository.save(log);
    }
}
