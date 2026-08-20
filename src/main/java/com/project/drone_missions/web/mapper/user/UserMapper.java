package com.project.drone_missions.web.mapper.user;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.dto.user.PublicUserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /** For someone looking at another account: no email. */
    public PublicUserResponse toPublicResponse(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isSuspended(),
                user.getCreatedAt()
        );
    }
}
