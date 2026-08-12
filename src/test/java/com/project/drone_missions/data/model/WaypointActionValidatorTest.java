package com.project.drone_missions.data.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain Bean Validation — no Spring context and no database, so this runs anywhere.
 * The property path matters as much as the failure: {@code GlobalExceptionHandler}
 * reports {@code getFieldErrors()} only, so a pathless class-level violation would vanish.
 */
class WaypointActionValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Waypoint waypoint(Double altitude, WaypointAction action, Integer hoverDurationSeconds) {
        return new Waypoint(45.0, 19.0, altitude, action, hoverDurationSeconds);
    }

    private static Set<String> propertyPaths(Waypoint waypoint) {
        return validator.validate(waypoint).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void photoWaypointWithoutHoverDurationIsValid() {
        assertThat(validator.validate(waypoint(50.0, WaypointAction.PHOTO, null))).isEmpty();
    }

    @Test
    void hoverWaypointWithPositiveDurationIsValid() {
        assertThat(validator.validate(waypoint(50.0, WaypointAction.HOVER, 30))).isEmpty();
    }

    @Test
    void altitudeIsRequired() {
        assertThat(propertyPaths(waypoint(null, WaypointAction.PHOTO, null))).contains("altitude");
    }

    @Test
    void altitudeMustBePositive() {
        assertThat(propertyPaths(waypoint(0.0, WaypointAction.PHOTO, null))).contains("altitude");
    }

    @Test
    void altitudeIsCappedAtTheLegalCeiling() {
        assertThat(propertyPaths(waypoint(120.1, WaypointAction.PHOTO, null))).contains("altitude");
        assertThat(validator.validate(waypoint(120.0, WaypointAction.PHOTO, null))).isEmpty();
    }

    @Test
    void actionIsRequired() {
        assertThat(propertyPaths(waypoint(50.0, null, null))).contains("action");
    }

    @Test
    void hoverWithoutDurationFailsOnTheDurationProperty() {
        assertThat(propertyPaths(waypoint(50.0, WaypointAction.HOVER, null)))
                .containsExactly("hoverDurationSeconds");
    }

    @Test
    void hoverWithNonPositiveDurationFailsOnTheDurationProperty() {
        assertThat(propertyPaths(waypoint(50.0, WaypointAction.HOVER, 0)))
                .containsExactly("hoverDurationSeconds");
        assertThat(propertyPaths(waypoint(50.0, WaypointAction.HOVER, -5)))
                .containsExactly("hoverDurationSeconds");
    }

    @Test
    void nonHoverActionRejectsADuration() {
        assertThat(propertyPaths(waypoint(50.0, WaypointAction.PHOTO, 10)))
                .containsExactly("hoverDurationSeconds");
        assertThat(propertyPaths(waypoint(50.0, WaypointAction.START_RECORDING, 10)))
                .containsExactly("hoverDurationSeconds");
    }
}
