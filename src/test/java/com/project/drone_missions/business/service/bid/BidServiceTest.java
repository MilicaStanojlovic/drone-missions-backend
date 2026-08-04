package com.project.drone_missions.business.service.bid;

import com.project.drone_missions.business.exception.bid.BidConflictException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.exception.user.UserSuspendedException;
import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.business.service.audit.NewAuditEntry;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.AuditAction;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private AuditService auditService;

    private BidService service;

    @BeforeEach
    void setUp() {
        service = new BidService(bidRepository, missionDao, userRepository, notificationService, emailService, auditService);
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
        verify(auditService, never()).record(any());
    }

    @Test
    void placingANewBidRecordsThePilot() {
        Mission mission = mission(MissionModeration.VISIBLE, user(7L, false));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, false)));
        when(bidRepository.findByMission_IdAndPilot_Id(1L, 5L)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> {
            Bid b = i.getArgument(0);
            b.setId(3L);
            return b;
        });

        service.place(1L, 5L, BigDecimal.TEN, null);

        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(5L);
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.BID_PLACED);
        assertThat(captor.getValue().targetId()).isEqualTo(3L);
        assertThat(captor.getValue().details()).doesNotContain("(updated)");
    }

    @Test
    void raisingAnExistingPendingBidRecordsItAsUpdated() {
        Mission mission = mission(MissionModeration.VISIBLE, user(7L, false));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, false)));
        Bid existing = new Bid();
        existing.setId(3L);
        existing.setMission(mission);
        existing.setPilot(user(5L, false));
        existing.setStatus(BidStatus.PENDING);
        when(bidRepository.findByMission_IdAndPilot_Id(1L, 5L)).thenReturn(Optional.of(existing));
        when(bidRepository.save(existing)).thenReturn(existing);

        service.place(1L, 5L, BigDecimal.ONE, null);

        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().details()).contains("(updated)");
    }

    @Test
    void withdrawingAPendingBidRecordsThePilot() {
        Mission mission = mission(MissionModeration.VISIBLE, user(7L, false));
        Bid bid = new Bid();
        bid.setId(3L);
        bid.setMission(mission);
        bid.setPilot(user(5L, false));
        bid.setStatus(BidStatus.PENDING);
        bid.setAmount(BigDecimal.TEN);
        when(bidRepository.findById(3L)).thenReturn(Optional.of(bid));

        service.withdraw(3L, 5L);

        verify(bidRepository).delete(bid);
        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.BID_WITHDRAWN);
        assertThat(captor.getValue().actorId()).isEqualTo(5L);
    }

    /** One row for the designer's intent — not one per rejected loser. */
    @Test
    void acceptingABidRecordsExactlyOnce() {
        Mission mission = mission(MissionModeration.VISIBLE, user(7L, false));
        Bid winner = new Bid();
        winner.setId(3L);
        winner.setMission(mission);
        winner.setPilot(user(5L, false));
        winner.setStatus(BidStatus.PENDING);
        winner.setAmount(BigDecimal.TEN);
        Bid loser = new Bid();
        loser.setId(4L);
        loser.setMission(mission);
        loser.setPilot(user(6L, false));
        loser.setStatus(BidStatus.PENDING);
        when(bidRepository.findById(3L)).thenReturn(Optional.of(winner));
        when(missionDao.findFresh(1L)).thenReturn(Optional.of(mission));
        when(bidRepository.findByMission_IdAndStatus(1L, BidStatus.PENDING))
                .thenReturn(List.of(winner, loser));

        service.accept(3L, 7L);

        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService, times(1)).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(7L);
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.BID_ACCEPTED);
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
        verify(auditService, never()).record(any());
    }
}
