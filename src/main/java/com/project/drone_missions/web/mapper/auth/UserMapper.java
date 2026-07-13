package com.project.drone_missions.web.mapper.auth;

import com.project.drone_missions.data.model.User;
import com.project.drone_missions.web.dto.auth.RegisterRequest;
import com.project.drone_missions.web.dto.auth.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /**
     * Builds a User entity from a registration request. The raw password is placed
     * in {@code passwordHash} as-is; {@code AuthService.register} replaces it with
     * the BCrypt hash before persisting, so a raw password never reaches the database.
     */
    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(request.password());
        return user;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
