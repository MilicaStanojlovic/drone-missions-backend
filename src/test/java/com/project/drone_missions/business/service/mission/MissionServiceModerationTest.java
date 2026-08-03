package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionConflictException;
import com.project.drone_missions.business.exception.user.UserSuspendedException;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionModeration;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/** Suspension enforcement and the hide/remove/restore state machine. */
@ExtendWith(MockitoExtension.class)
class MissionServiceModerationTest {

    @Mock
    private MissionDao missionDao;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;

    private MissionService service;

    @BeforeEach
    void setUp() {
        service = new MissionService(missionDao, bidRepository, userRepository, notificationService, emailService);
    }

    private static User user(Long id, boolean suspended) {
        User u = new User();
        u.setId(id);
        u.setSuspendedAt(suspended ? Instant.now() : null);
        return u;
    }

    private static Mission mission(MissionStatus status, MissionModeration moderation) {
        Mission m = new Mission();
        m.setId(1L);
        m.setStatus(status);
        m.setModeration(moderation);
        return m;
    }

    @Test
    void createRejectsSuspendedDesigner() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, true)));

        assertThatThrownBy(() -> service.create(new Mission(), 7L))
                .isInstanceOf(UserSuspendedException.class);
        verify(missionDao, never()).save(any());
    }

    @Test
    void startRejectsSuspendedPilot() {
        Mission mission = mission(MissionStatus.AWARDED, MissionModeration.VISIBLE);
        mission.setAwardedPilot(user(5L, true));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.start(1L, 5L))
                .isInstanceOf(UserSuspendedException.class);
    }

    @Test
    void completeRejectsSuspendedPilot() {
        Mission mission = mission(MissionStatus.IN_PROGRESS, MissionModeration.VISIBLE);
        mission.setAwardedPilot(user(5L, true));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.complete(1L, 5L))
                .isInstanceOf(UserSuspendedException.class);
    }

    @Test
    void hideMovesVisibleToHidden() {
        Mission mission = mission(MissionStatus.PUBLISHED, MissionModeration.VISIBLE);
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));
        when(missionDao.save(mission)).thenReturn(mission);

        assertThat(service.hide(1L).getModeration()).isEqualTo(MissionModeration.HIDDEN);
    }

    @Test
    void hideRejectsAlreadyHidden() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionStatus.PUBLISHED, MissionModeration.HIDDEN)));

        assertThatThrownBy(() -> service.hide(1L)).isInstanceOf(MissionConflictException.class);
    }

    @Test
    void removeWorksFromHiddenToo() {
        Mission mission = mission(MissionStatus.PUBLISHED, MissionModeration.HIDDEN);
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));
        when(missionDao.save(mission)).thenReturn(mission);

        assertThat(service.remove(1L).getModeration()).isEqualTo(MissionModeration.REMOVED);
    }

    @Test
    void removeRejectsAlreadyRemoved() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionStatus.PUBLISHED, MissionModeration.REMOVED)));

        assertThatThrownBy(() -> service.remove(1L)).isInstanceOf(MissionConflictException.class);
    }

    @Test
    void restoreOnlyFromRemoved() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionStatus.PUBLISHED, MissionModeration.VISIBLE)));

        assertThatThrownBy(() -> service.restore(1L)).isInstanceOf(MissionConflictException.class);
    }
}
