package com.project.drone_missions.web.controller.user;

import com.project.drone_missions.business.service.auth.AuthService;
import com.project.drone_missions.business.service.user.UserService;
import com.project.drone_missions.security.UserPrincipal;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.dto.user.NewAdminRequest;
import com.project.drone_missions.web.dto.user.PublicUserResponse;
import com.project.drone_missions.web.mapper.user.UserMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserMapper mapper;

    /** Full UserResponse (with email) on purpose — this is the admin view. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> all() {
        return ResponseEntity.ok(userService.findAll().stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(userService.findById(userId)));
    }

    /** Public view of another account, for the profile page behind a rating. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PublicUserResponse> byId(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toPublicResponse(userService.findById(id)));
    }

    /** An admin registers another admin — the only path that can mint one at runtime. */
    @PostMapping("/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createAdmin(@Valid @RequestBody NewAdminRequest request,
                                                    @AuthenticationPrincipal long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(
                authService.createAdmin(request.username(), request.email(), request.password(), userId)));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> suspend(@PathVariable Long id,
                                                @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(userService.suspend(id, userId)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> reactivate(@PathVariable Long id,
                                                   @AuthenticationPrincipal long userId) {
        return ResponseEntity.ok(mapper.toResponse(userService.reactivate(id, userId)));
    }
}
