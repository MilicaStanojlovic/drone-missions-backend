package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.AuditLogRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;
    @Mock
    private UserRepository userRepository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, userRepository);
    }

    @Test
    void recordMapsEveryFieldOntoTheSavedRow() {
        User actor = new User();
        actor.setId(9L);
        when(userRepository.getReferenceById(9L)).thenReturn(actor);

        service.record(new NewAuditEntry(9L, UserRole.ADMIN, AuditAction.MISSION_HIDDEN,
                AuditTargetType.MISSION, 4L, "\"Orchard survey\""));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isSameAs(actor);
        assertThat(saved.getActorRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getAction()).isEqualTo(AuditAction.MISSION_HIDDEN);
        assertThat(saved.getTargetType()).isEqualTo(AuditTargetType.MISSION);
        assertThat(saved.getTargetId()).isEqualTo(4L);
        assertThat(saved.getDetails()).isEqualTo("\"Orchard survey\"");
    }

    @Test
    void blankSearchIsNormalizedToNullAndPaddedSearchIsTrimmed() {
        Pageable pageable = PageRequest.of(0, 20);

        service.search(null, null, null, "   ", pageable);
        verify(repository).search(null, null, null, null, pageable);

        service.search(null, null, null, " orchard ", pageable);
        verify(repository).search(null, null, null, "orchard", pageable);
    }
}
