package com.project.drone_missions.web.dto.auth;

/** Bearer token returned on successful login; clients send it as `Authorization: Bearer <token>`. */
public record LoginResponse(
        String token
) {
}
