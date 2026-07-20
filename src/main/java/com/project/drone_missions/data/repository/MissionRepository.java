package com.project.drone_missions.data.repository;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByUserId(Long userId);

    List<Mission> findByStatusIn(Collection<MissionStatus> statuses);
}
