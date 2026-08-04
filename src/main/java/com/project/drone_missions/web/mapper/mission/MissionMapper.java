package com.project.drone_missions.web.mapper.mission;

import com.project.drone_missions.business.service.rating.RatingSummary;
import com.project.drone_missions.web.dto.mission.MissionRequest;
import com.project.drone_missions.web.dto.mission.MissionResponse;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
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
        mission.setLocation(request.location());
        mission.setBiddingDeadline(request.biddingDeadline());
        mission.setWaypoints(request.waypoints());
        mission.setGeofence(request.geofence());
        return mission;
    }

    /**
     * The caller supplies the designer's rating rather than the mapper fetching it, so a list
     * of missions costs one aggregate query instead of one per row.
     */
    public MissionResponse toResponse(Mission mission, RatingSummary designerRating) {
        User designer = mission.getDesigner();
        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getStatus(),
                mission.getModeration(),
                mission.getDesignerId(),
                designer == null ? null : designer.getEmail(),
                designer == null ? null : designer.getUsername(),
                designer != null && designer.isSuspended(),
                designerRating.average(),
                designerRating.count(),
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
