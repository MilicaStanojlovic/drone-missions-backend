package com.project.drone_missions.business.service.notification;

import com.project.drone_missions.data.repository.NotificationRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link NotificationService#markAllRead} used to select the caller's whole inbox and then
 * call {@code save} once per unread row. These tests pin that it now issues a single bulk
 * update instead — no per-row select or save.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 42L;

    @Mock
    private NotificationRepository repository;

    @Mock
    private UserRepository userRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, userRepository);
    }

    @Test
    void markAllReadIssuesASingleBulkUpdateInsteadOfOnePerNotification() {
        service.markAllRead(USER_ID);

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).markAllReadForUser(eq(USER_ID), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isCloseTo(Instant.now(), within(2, ChronoUnit.SECONDS));

        verify(repository, never()).findByUser_IdOrderByCreatedAtDesc(any());
        verify(repository, never()).save(any());
    }
}
