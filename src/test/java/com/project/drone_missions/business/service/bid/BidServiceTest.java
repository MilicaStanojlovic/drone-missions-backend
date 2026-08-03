package com.project.drone_missions.business.service.bid;

import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDataAccess;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import com.project.drone_missions.data.model.Mission;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BidService#accept} decides every other pending bid on the mission. These tests pin
 * that this now happens as one bulk update ({@link BidRepository#updateStatusForOtherBids})
 * rather than a {@code save} per losing bid, while each loser is still notified individually.
 */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;
    @Mock
    private MissionDataAccess missionDataAccess;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;

    private BidService service;

    @BeforeEach
    void setUp() {
        service = new BidService(bidRepository, missionDataAccess, userRepository, notificationService, emailService);
    }

    @Test
    void acceptRejectsOtherPendingBidsInOneBulkUpdateInsteadOfSavingEach() {
        User designer = new User(1L, "designer", "designer@example.com", "hash", null, null, null);
        User winningPilot = new User(10L, "winner", "winner@example.com", "hash", null, null, null);
        User losingPilot = new User(20L, "loser", "loser@example.com", "hash", null, null, null);

        Mission mission = new Mission();
        mission.setId(100L);
        mission.setStatus(MissionStatus.BIDDING);
        mission.setDesigner(designer);

        Bid winningBid = new Bid();
        winningBid.setId(1L);
        winningBid.setMission(mission);
        winningBid.setPilot(winningPilot);
        winningBid.setAmount(BigDecimal.TEN);
        winningBid.setStatus(BidStatus.PENDING);

        Bid losingBid = new Bid();
        losingBid.setId(2L);
        losingBid.setMission(mission);
        losingBid.setPilot(losingPilot);
        losingBid.setAmount(BigDecimal.ONE);
        losingBid.setStatus(BidStatus.PENDING);

        when(bidRepository.findById(1L)).thenReturn(Optional.of(winningBid));
        when(missionDataAccess.findFresh(100L)).thenReturn(Optional.of(mission));
        when(bidRepository.findByMission_IdAndStatus(100L, BidStatus.PENDING))
                .thenReturn(List.of(winningBid, losingBid));

        service.accept(1L, 1L);

        verify(bidRepository).updateStatusForOtherBids(100L, 1L, BidStatus.PENDING, BidStatus.REJECTED);
        verify(bidRepository, never()).save(losingBid);
        verify(notificationService, times(2)).create(any());
    }

    @Test
    void acceptSkipsTheBulkUpdateWhenThereAreNoOtherPendingBids() {
        User designer = new User(1L, "designer", "designer@example.com", "hash", null, null, null);
        User winningPilot = new User(10L, "winner", "winner@example.com", "hash", null, null, null);

        Mission mission = new Mission();
        mission.setId(100L);
        mission.setStatus(MissionStatus.BIDDING);
        mission.setDesigner(designer);

        Bid winningBid = new Bid();
        winningBid.setId(1L);
        winningBid.setMission(mission);
        winningBid.setPilot(winningPilot);
        winningBid.setAmount(BigDecimal.TEN);
        winningBid.setStatus(BidStatus.PENDING);

        when(bidRepository.findById(1L)).thenReturn(Optional.of(winningBid));
        when(missionDataAccess.findFresh(100L)).thenReturn(Optional.of(mission));
        when(bidRepository.findByMission_IdAndStatus(100L, BidStatus.PENDING))
                .thenReturn(List.of(winningBid));

        service.accept(1L, 1L);

        verify(bidRepository, never()).updateStatusForOtherBids(any(), any(), any(), any());
    }
}
