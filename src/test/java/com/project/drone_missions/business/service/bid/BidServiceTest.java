package com.project.drone_missions.business.service.bid;

import com.project.drone_missions.business.exception.bid.BidConflictException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.exception.user.UserSuspendedException;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Moderation enforcement on bidding: hidden missions 404, suspended actors are blocked. */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;
    @Mock
    private MissionDao missionDao;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;

    private BidService service;

    @BeforeEach
    void setUp() {
        service = new BidService(bidRepository, missionDao, userRepository, notificationService, emailService);
    }

    private static User user(Long id, boolean suspended) {
        User u = new User();
        u.setId(id);
        u.setSuspendedAt(suspended ? Instant.now() : null);
        return u;
    }

    private static Mission mission(MissionModeration moderation, User designer) {
        Mission m = new Mission();
        m.setId(1L);
        m.setStatus(MissionStatus.PUBLISHED);
        m.setModeration(moderation);
        m.setDesigner(designer);
        return m;
    }

    @Test
    void placeOnHiddenMissionReadsAsNotFound() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionModeration.HIDDEN, user(7L, false))));

        assertThatThrownBy(() -> service.place(1L, 5L, BigDecimal.TEN, null))
                .isInstanceOf(MissionNotFoundException.class);
    }

    @Test
    void placeOnSuspendedDesignersMissionReadsAsNotFound() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionModeration.VISIBLE, user(7L, true))));

        assertThatThrownBy(() -> service.place(1L, 5L, BigDecimal.TEN, null))
                .isInstanceOf(MissionNotFoundException.class);
    }

    @Test
    void placeBySuspendedPilotRejected() {
        when(missionDao.findFresh(1L))
                .thenReturn(Optional.of(mission(MissionModeration.VISIBLE, user(7L, false))));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, true)));

        assertThatThrownBy(() -> service.place(1L, 5L, BigDecimal.TEN, null))
                .isInstanceOf(UserSuspendedException.class);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void acceptFrozenWhilePilotSuspended() {
        Mission mission = mission(MissionModeration.VISIBLE, user(7L, false));
        Bid bid = new Bid();
        bid.setId(3L);
        bid.setMission(mission);
        bid.setPilot(user(5L, true));
        bid.setStatus(BidStatus.PENDING);
        when(bidRepository.findById(3L)).thenReturn(Optional.of(bid));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.accept(3L, 7L))
                .isInstanceOf(BidConflictException.class)
                .hasMessageContaining("suspended");
        verify(bidRepository, never()).save(any());
    }
}
