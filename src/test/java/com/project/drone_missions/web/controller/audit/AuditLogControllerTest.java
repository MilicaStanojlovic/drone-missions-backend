package com.project.drone_missions.web.controller.audit;

import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditLog;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.web.dto.audit.AuditLogResponse;
import com.project.drone_missions.web.mapper.audit.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditService service;

    private AuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditLogController(service, new AuditLogMapper());
    }

    private static AuditLog row() {
        User admin = new User();
        admin.setId(9L);
        admin.setUsername("admin");
        return new AuditLog(1L, admin, UserRole.ADMIN, AuditAction.MISSION_HIDDEN,
                AuditTargetType.MISSION, 4L, "\"Orchard survey\"", Instant.EPOCH);
    }

    @Test
    void listMapsRowsIntoThePagedEnvelope() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLog> page = new PageImpl<>(List.of(row()), pageable, 1);
        when(service.search(null, null, null, null, pageable)).thenReturn(page);

        PagedModel<AuditLogResponse> body = controller.list(null, null, null, null, pageable).getBody();

        assertThat(body.getContent()).hasSize(1);
        AuditLogResponse response = body.getContent().getFirst();
        assertThat(response.actorId()).isEqualTo(9L);
        assertThat(response.actorUsername()).isEqualTo("admin");
        assertThat(response.action()).isEqualTo(AuditAction.MISSION_HIDDEN);
        assertThat(response.targetId()).isEqualTo(4L);
        assertThat(body.getMetadata().totalElements()).isEqualTo(1);
    }

    @Test
    void filtersAndPageablePassThroughToTheService() {
        Pageable pageable = PageRequest.of(2, 5);
        when(service.search(9L, AuditAction.USER_SUSPENDED, UserRole.PILOT, "orchard", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        controller.list(9L, AuditAction.USER_SUSPENDED, UserRole.PILOT, "orchard", pageable);

        verify(service).search(9L, AuditAction.USER_SUSPENDED, UserRole.PILOT, "orchard", pageable);
    }
}
