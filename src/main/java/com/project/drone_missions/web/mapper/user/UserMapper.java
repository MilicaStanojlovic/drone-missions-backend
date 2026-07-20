package com.project.drone_missions.web.mapper.user;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.web.dto.auth.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
