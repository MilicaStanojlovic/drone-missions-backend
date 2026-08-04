package com.project.drone_missions.business.service.user;

import com.project.drone_missions.business.exception.user.AdminCannotBeSuspendedException;
import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.business.service.audit.NewAuditEntry;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;
    @Mock
    private MissionDao missionDao;
    @Mock
    private AuditService auditService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, missionDao, auditService);
    }

    private static User user(UserRole role, boolean suspended) {
        User u = new User();
        u.setId(3L);
        u.setUsername("pilot-mira");
        u.setRole(role);
        u.setSuspendedAt(suspended ? Instant.now() : null);
        return u;
    }

    @Test
    void suspendWritesAndRecordsTheAdminWhoDidIt() {
        User target = user(UserRole.PILOT, false);
        when(repository.findById(3L)).thenReturn(Optional.of(target));

        service.suspend(3L, 9L);

        assertThat(target.isSuspended()).isTrue();
        verify(repository).save(target);
        verify(missionDao).invalidateLists();
        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(9L);
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.USER_SUSPENDED);
        assertThat(captor.getValue().targetId()).isEqualTo(3L);
    }

    @Test
    void suspendingAnAlreadySuspendedUserRecordsNothing() {
        when(repository.findById(3L)).thenReturn(Optional.of(user(UserRole.PILOT, true)));

        service.suspend(3L, 9L);

        verify(repository, never()).save(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void suspendingAnAdminIsRejectedAndRecordsNothing() {
        when(repository.findById(3L)).thenReturn(Optional.of(user(UserRole.ADMIN, false)));

        assertThatThrownBy(() -> service.suspend(3L, 9L))
                .isInstanceOf(AdminCannotBeSuspendedException.class);
        verify(auditService, never()).record(any());
    }

    @Test
    void reactivateWritesAndRecordsTheAdminWhoDidIt() {
        User target = user(UserRole.PILOT, true);
        when(repository.findById(3L)).thenReturn(Optional.of(target));

        service.reactivate(3L, 9L);

        assertThat(target.isSuspended()).isFalse();
        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.USER_REACTIVATED);
        assertThat(captor.getValue().actorId()).isEqualTo(9L);
    }

    @Test
    void reactivatingAnActiveUserRecordsNothing() {
        when(repository.findById(3L)).thenReturn(Optional.of(user(UserRole.PILOT, false)));

        service.reactivate(3L, 9L);

        verify(repository, never()).save(any());
        verify(auditService, never()).record(any());
    }
}
