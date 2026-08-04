package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.data.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Null filters mean "not filtering". {@code q} is unescaped (% and _ act as
     * wildcards), matching the mission feed's keyword filter. The two LIKEs stay
     * separate, not concat(username, details): details is nullable, and concat
     * with null would drop rows whose username matches.
     */
    @Query("""
            select a from AuditLog a
            where (:actorId is null or a.actor.id = :actorId)
              and (:action is null or a.action = :action)
              and (:role is null or a.actorRole = :role)
              and (:q is null
                   or lower(a.actor.username) like concat('%', lower(:q), '%')
                   or lower(a.details) like concat('%', lower(:q), '%'))
            """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
                          @Param("action") AuditAction action,
                          @Param("role") UserRole role,
                          @Param("q") String q,
                          Pageable pageable);
}
