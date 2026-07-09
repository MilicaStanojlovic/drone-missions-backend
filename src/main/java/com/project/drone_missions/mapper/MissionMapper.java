package com.project.drone_missions.mapper;

import com.project.drone_missions.dto.MissionRequest;
import com.project.drone_missions.dto.MissionResponse;
import com.project.drone_missions.model.Mission;
import org.springframework.stereotype.Component;

@Component
public class MissionMapper {

    public Mission toEntity(MissionRequest request) {
        Mission mission = new Mission();
        apply(request, mission);
        return mission;
    }

    public void apply(MissionRequest request, Mission mission) {
        mission.setName(request.name());
        mission.setDescription(request.description());
        mission.setStatus(request.status());
        mission.setStartTime(request.startTime());
        mission.setEndTime(request.endTime());
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
