package com.project.drone_missions.data.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One immutable row per state-changing user action — who, did what, to what, when.
 * The target is a (type, id) pair rather than an association because the history
 * must outlive deletable targets; {@code details} snapshots the context instead.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** The actor's role at the time of the action, so rows stay self-describing. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String details;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public Long getActorId() {
        return actor == null ? null : actor.getId();
    }
}
