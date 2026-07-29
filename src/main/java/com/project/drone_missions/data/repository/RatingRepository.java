package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByMissionIdAndRaterId(Long missionId, Long raterId);

    List<Rating> findByRateeIdOrderByCreatedAtDesc(Long rateeId);

    List<Rating> findByMissionIdOrderByCreatedAtDesc(Long missionId);

    /**
     * One query for a whole page of missions, so feed cards never cost a lookup per card.
     * Users with no ratings are absent from the result rather than returned as zero.
     */
    @Query("""
            select r.rateeId as rateeId, avg(r.score) as average, count(r) as total
            from Rating r
            where r.rateeId in :rateeIds
            group by r.rateeId
            """)
    List<RateeSummary> summariesFor(@Param("rateeIds") Collection<Long> rateeIds);

    /** Spring Data projection — keeps the aggregate typed instead of an Object[] row. */
    interface RateeSummary {
        Long getRateeId();

        Double getAverage();

        Long getTotal();
    }
}
