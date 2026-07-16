package com.project.drone_missions.web.controller.user;

import com.project.drone_missions.business.service.user.UserService;
import com.project.drone_missions.security.CurrentUserId;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.mapper.user.UserMapper;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper mapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@CurrentUserId Long userId) {
        return ResponseEntity.ok(mapper.toResponse(userService.findById(userId)));
    }
}
