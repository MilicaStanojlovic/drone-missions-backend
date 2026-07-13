package com.project.drone_missions.web.mapper.mission;

import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.data.model.Mission;
import org.springframework.stereotype.Component;

@Component
public class MissionMapper {

    public Mission toEntity(MissionRequest request) {
        Mission mission = new Mission();
        mission.setName(request.name());
        mission.setDescription(request.description());
        mission.setStatus(request.status());
        mission.setStartTime(request.startTime());
        mission.setEndTime(request.endTime());
        return mission;
    }

    public MissionResponse toResponse(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getStatus(),
                mission.getStartTime(),
                mission.getEndTime(),
                mission.getCreatedAt(),
                mission.getUpdatedAt()
        );
    }
}
