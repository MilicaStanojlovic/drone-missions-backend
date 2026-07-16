package com.project.drone_missions.web.controller.auth;

import com.project.drone_missions.business.service.auth.AuthService;
import com.project.drone_missions.business.service.auth.LoginResult;
import com.project.drone_missions.web.dto.auth.LoginRequest;
import com.project.drone_missions.web.dto.auth.RegisterRequest;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.mapper.user.UserMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper mapper;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = mapper.toResponse(authService.createUser(
                request.username(), request.email(), request.password(), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * On success the JWT is returned in the {@code Authorization} response header
     * (as {@code Bearer <token>}) and the authenticated user's profile in the body.
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.token())
                .body(mapper.toResponse(result.user()));
    }

    /**
     * Logout is a client-side concern for stateless JWTs — the client simply
     * discards the token. Exposed for API symmetry; returns 204 and does nothing
     * server-side. (Server-side invalidation would require a token blacklist.)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
