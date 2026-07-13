package com.project.drone_missions.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's id into a controller method parameter.
 * Meta-annotated with {@link AuthenticationPrincipal}: the JWT filter stores the
 * user id as the authentication principal, so {@code @CurrentUserId Long userId}
 * resolves to it. On an unauthenticated request the value is null — but the
 * security rules reject those before the controller runs.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUserId {
}
