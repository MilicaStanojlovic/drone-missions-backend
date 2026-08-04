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
     * Null filters mean "not filtering". {@code pattern} arrives pre-built by
     * AuditService as a lowercase %…% LIKE pattern: lower(:param) here breaks on
     * PostgreSQL, which can't type a null inside a function (lower(bytea)). Two
     * LIKEs, not concat — details is nullable and concat would null out.
     */
    @Query("""
            select a from AuditLog a
            where (:actorId is null or a.actor.id = :actorId)
              and (:action is null or a.action = :action)
              and (:role is null or a.actorRole = :role)
              and (:pattern is null
                   or lower(a.actor.username) like :pattern
                   or lower(a.details) like :pattern)
            """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
                          @Param("action") AuditAction action,
                          @Param("role") UserRole role,
                          @Param("pattern") String pattern,
                          Pageable pageable);
}
