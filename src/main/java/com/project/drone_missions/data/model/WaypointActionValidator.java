package com.project.drone_missions.data.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * HOVER requires a positive {@code hoverDurationSeconds}; every other action requires none.
 * Violations are attached to that property so they arrive as a field error —
 * {@code GlobalExceptionHandler} builds its response from {@code getFieldErrors()} only.
 */
public class WaypointActionValidator implements ConstraintValidator<ValidWaypointAction, Waypoint> {

    private static final String PROPERTY = "hoverDurationSeconds";

    @Override
    public boolean isValid(Waypoint waypoint, ConstraintValidatorContext context) {
        // a missing action is already reported by @NotNull
        if (waypoint == null || waypoint.action() == null) {
            return true;
        }
        Integer hoverDurationSeconds = waypoint.hoverDurationSeconds();
        if (waypoint.action() == WaypointAction.HOVER) {
            if (hoverDurationSeconds == null || hoverDurationSeconds <= 0) {
                return reject(context, "must be greater than 0 for a HOVER waypoint");
            }
            return true;
        }
        if (hoverDurationSeconds != null) {
            return reject(context, "is only allowed on a HOVER waypoint");
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(PROPERTY)
                .addConstraintViolation();
        return false;
    }
}
