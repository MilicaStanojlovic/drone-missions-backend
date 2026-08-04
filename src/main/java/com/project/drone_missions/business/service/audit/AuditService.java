package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.AuditLogRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * The admin listing; null/blank filters mean "everything". The LIKE pattern
     * is lowercased here, not in the query — see AuditLogRepository. q is
     * unescaped (% and _ act as wildcards), like the mission feed's keyword.
     */
    public Page<AuditLog> search(Long actorId, AuditAction action, UserRole role,
                                 String q, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return repository.search(actorId, action, role, pattern, pageable);
    }
}
