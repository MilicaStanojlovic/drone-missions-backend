package com.project.drone_missions.data.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ties a waypoint's hover duration to its action; see {@link WaypointActionValidator}. */
@Documented
@Constraint(validatedBy = WaypointActionValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWaypointAction {

    String message() default "invalid hoverDurationSeconds for this action";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
