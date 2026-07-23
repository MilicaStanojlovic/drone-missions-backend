package com.project.drone_missions.web.mapper.mission;

import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class MissionMapper {

    private final UserRepository userRepository;

    public MissionMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mission toEntity(MissionRequest request) {
        Mission mission = new Mission();
        mission.setName(request.name());
        mission.setDescription(request.description());
        mission.setStatus(request.status());
        mission.setStartTime(request.startTime());
        mission.setEndTime(request.endTime());
        mission.setLocation(request.location());
        mission.setBiddingDeadline(request.biddingDeadline());
        mission.setWaypoints(request.waypoints());
        mission.setGeofence(request.geofence());
        return mission;
    }

    public MissionResponse toResponse(Mission mission) {
        String designerEmail = userRepository.findById(mission.getUserId())
                .map(User::getEmail)
                .orElse(null);
        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getStatus(),
                mission.getUserId(),
                designerEmail,
                mission.getAwardedPilotId(),
                mission.getStartTime(),
                mission.getEndTime(),
                mission.getLocation(),
                mission.getBiddingDeadline(),
                mission.getWaypoints(),
                mission.getGeofence(),
                mission.getCreatedAt(),
                mission.getUpdatedAt()
        );
    }
}
