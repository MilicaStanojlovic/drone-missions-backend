package com.project.drone_missions.web.controller.notification;

import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.web.dto.notification.NotificationResponse;
import com.project.drone_missions.web.mapper.notification.NotificationMapper;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationMapper mapper;

    /** The caller's notifications, newest first. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationResponse>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.listFor(userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** The caller's unread count (for the bell badge). */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Map.of("count", service.unreadCount(userId)));
    }

    /** Mark one notification read. */
    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        service.markRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** Mark all of the caller's notifications read. */
    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
        service.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
