package com.project.drone_missions.web;

import com.project.drone_missions.business.ConflictException;
import com.project.drone_missions.business.ForbiddenException;
import com.project.drone_missions.business.NotFoundException;
import com.project.drone_missions.business.UnauthorizedException;
import lombok.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Immutable error payload returned for every handled exception. */
    @Builder
    public record ErrorResponse<T>(T data, HttpStatus status, String message) {
    }

    /** Bean-validation failures (e.g. @NotBlank, @NotNull) -> 400 with per-field messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.<Map<String, String>>builder()
                        .data(getValidationErrors(exception))
                        .status(HttpStatus.BAD_REQUEST)
                        .message("Data validation failed")
                        .build());
    }

    /** Malformed JSON or an unknown enum value -> 400 (a client error, never a 500). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .message("Malformed or unreadable request body")
                        .build());
    }

    /** A query/path parameter of the wrong type (e.g. an unknown enum value) -> 400, not 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .message("Invalid value for parameter '%s'".formatted(exception.getName()))
                        .build());
    }

    /** Any not-found in the domain (MissionNotFoundException, ...) -> 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse<Void>> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.NOT_FOUND)
                        .message(exception.getMessage())
                        .build());
    }

    /** Invalid login credentials surfaced from the business layer -> 401. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse<Void>> handleUnauthorized(UnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.UNAUTHORIZED)
                        .message(exception.getMessage())
                        .build());
    }

    /** Authenticated but not permitted (e.g. editing someone else's mission) -> 403. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse<Void>> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.FORBIDDEN)
                        .message(exception.getMessage())
                        .build());
    }

    /** Method-security denial from @PreAuthorize (e.g. a pilot creating a mission) -> 403. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.FORBIDDEN)
                        .message("You do not have permission to perform this action")
                        .build());
    }

    /** Conflict with existing state (e.g. duplicate email) -> 409. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse<Void>> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.CONFLICT)
                        .message(exception.getMessage())
                        .build());
    }

    /** Catch-all for anything unhandled -> 500 with a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<Void>> handleGeneric(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.<Void>builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("An unexpected error occurred")
                        .build());
    }

    private Map<String, String> getValidationErrors(MethodArgumentNotValidException exception) {
        return exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
                ));
    }
}
