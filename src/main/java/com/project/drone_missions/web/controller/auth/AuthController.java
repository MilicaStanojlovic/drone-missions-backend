package com.project.drone_missions.web.controller.auth;

import com.project.drone_missions.business.service.auth.AuthService;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.security.CurrentUserId;
import com.project.drone_missions.security.JwtTokenProvider;
import com.project.drone_missions.web.dto.auth.LoginRequest;
import com.project.drone_missions.web.dto.auth.LoginResponse;
import com.project.drone_missions.web.dto.auth.RegisterRequest;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.mapper.auth.UserMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final UserMapper mapper;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User created = service.register(mapper.toEntity(request));
        return mapper.toResponse(created);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = service.authenticate(request.email(), request.password());
        return new LoginResponse(tokenProvider.generateToken(user.getId()));
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId Long userId) {
        return mapper.toResponse(service.getById(userId));
    }

    /**
     * Logout is a client-side concern for stateless JWTs — the client simply
     * discards the token. Exposed for API symmetry; returns 204 and does nothing
     * server-side. (Server-side invalidation would require a token blacklist.)
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }
}
