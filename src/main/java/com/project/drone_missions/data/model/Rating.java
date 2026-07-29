package com.project.drone_missions.data.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * No {@code updatedAt}: a rating is written once and never changed, which the unique
 * constraint on (mission_id, rater_id) enforces.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long missionId;

//    @OneToOne
//    private Mission mission;

    @Column(nullable = false)
    private Long raterId;

    @Column(nullable = false)
    private Long rateeId;

    @Column(nullable = false)
    private Short score;

    @Column(length = 500)
    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
