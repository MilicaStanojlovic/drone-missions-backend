package com.project.drone_missions.data.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mission { // TODO Base Entity with id, createdAt, updatedAt with inheritance

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionStatus status;

    // Id of the user who created and owns this mission. Nullable for legacy
    // missions created before authentication existed; always set for new ones.
    private Long userId;

    // Id of the pilot whose bid was accepted. Null until the mission is awarded;
    // set (with status → AWARDED) when the designer accepts a bid.
    private Long awardedPilotId;

    private Instant startTime;

    private Instant endTime;

    // ---- flight plan (nullable until the mission is planned) ----

    private String location;

    private LocalDate biddingDeadline;

    /** Ordered route, stored as a JSON array of lat/lng points. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<GeoPoint> waypoints;

    /** Flight zone (circle or polygon), stored as a JSON object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Geofence geofence;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
