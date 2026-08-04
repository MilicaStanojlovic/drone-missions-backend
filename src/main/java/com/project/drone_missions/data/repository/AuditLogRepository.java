package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Null filters mean "not filtering", mirroring the mission feed's convention. */
    @Query("""
            select a from AuditLog a
            where (:actorId is null or a.actor.id = :actorId)
              and (:action is null or a.action = :action)
            """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
                          @Param("action") AuditAction action,
                          Pageable pageable);
}
