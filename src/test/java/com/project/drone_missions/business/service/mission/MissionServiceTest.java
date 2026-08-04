package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.access.OpenMissionQuery;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MissionService#findOpen} builds the {@link OpenMissionQuery} that both cache
 * implementations key list results on. These tests pin the normalisation that keeps
 * case-different searches for the same thing (e.g. "Novi Sad" vs. "novi sad") from becoming
 * two distinct, duplicate cache entries — see issue #12.
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionDao repository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private MissionService service;

    @BeforeEach
    void setUp() {
        service = new MissionService(repository, bidRepository, userRepository, notificationService, emailService, auditService);
        when(repository.findOpen(any())).thenReturn(List.of());
    }

    @Test
    void lowercasesAndTrimsLocationAndKeyword() {
        service.findOpen("  Novi Sad  ", "  DRONE  ", null);

        OpenMissionQuery query = capturedQuery();
        assertThat(query.location()).isEqualTo("novi sad");
        assertThat(query.keyword()).isEqualTo("drone");
    }

    @Test
    void blankFiltersBecomeNull() {
        service.findOpen("   ", "", null);

        OpenMissionQuery query = capturedQuery();
        assertThat(query.location()).isNull();
        assertThat(query.keyword()).isNull();
    }

    @Test
    void nullFiltersStayNull() {
        service.findOpen(null, null, null);

        OpenMissionQuery query = capturedQuery();
        assertThat(query.location()).isNull();
        assertThat(query.keyword()).isNull();
    }

    @Test
    void searchesDifferingOnlyByCaseProduceAnEqualCacheKey() {
        service.findOpen("Novi Sad", "Drone", null);
        OpenMissionQuery first = capturedQuery();

        service.findOpen("novi sad", "DRONE", null);
        OpenMissionQuery second = capturedQuery();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void statusesAreAlwaysPublishedAndBidding() {
        service.findOpen(null, null, null);

        assertThat(capturedQuery().statuses())
                .isEqualTo(Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING));
    }

    private OpenMissionQuery capturedQuery() {
        ArgumentCaptor<OpenMissionQuery> captor = ArgumentCaptor.forClass(OpenMissionQuery.class);
        verify(repository, atLeastOnce()).findOpen(captor.capture());
        return captor.getValue();
    }
}
