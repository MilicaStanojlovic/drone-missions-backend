package com.project.drone_missions.business.service.bid;

import com.project.drone_missions.business.exception.bid.BidConflictException;
import com.project.drone_missions.business.exception.bid.BidNotFoundException;
import com.project.drone_missions.business.exception.mission.MissionAccessDeniedException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.mail.NewBidEmail;
import com.project.drone_missions.business.service.notification.NewNotification;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class BidService {

    /** Bids can only be placed/updated while the mission is open for offers. */
    private static final Set<MissionStatus> BIDDABLE_STATUSES =
            Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING);

    private final BidRepository bidRepository;
    private final MissionDao missionDao;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /**
     * Place the caller's bid on a mission, or update it if a pending one already
     * exists (one bid per pilot per mission). The first bid on a PUBLISHED
     * mission flips it to BIDDING so the lifecycle reflects real activity.
     */
    public Bid place(Long missionId, Long pilotId, BigDecimal amount, String message) {
        // Fresh, not cached: the first bid on a PUBLISHED mission writes it back as BIDDING.
        Mission mission = getFreshMissionOrThrow(missionId);
        if (!BIDDABLE_STATUSES.contains(mission.getStatus())) {
            throw new BidConflictException(
                    "Mission %d is not open for bidding".formatted(missionId));
        }
        // The deadline day itself is still open; bidding closes once it has passed.
        if (mission.getBiddingDeadline() != null
                && LocalDate.now().isAfter(mission.getBiddingDeadline())) {
            throw new BidConflictException(
                    "The bidding deadline for mission %d has passed".formatted(missionId));
        }

        Bid bid = bidRepository.findByMission_IdAndPilot_Id(missionId, pilotId)
                .orElseGet(() -> {
                    Bid fresh = new Bid();
                    fresh.setMission(mission);
                    fresh.setPilot(userRepository.getReferenceById(pilotId));
                    fresh.setStatus(BidStatus.PENDING);
                    return fresh;
                });
        if (bid.getId() != null && bid.getStatus() != BidStatus.PENDING) {
            throw new BidConflictException(
                    "Bid %d has already been decided and cannot be changed".formatted(bid.getId()));
        }
        bid.setAmount(amount);
        bid.setMessage(message);
        Bid saved = bidRepository.save(bid);

        if (mission.getStatus() == MissionStatus.PUBLISHED) {
            mission.setStatus(MissionStatus.BIDDING);
            missionDao.save(mission);
        }

        // Let the mission's owner know a bid came in (best-effort email).
        User designer = mission.getDesigner();
        String pilotName = userRepository.findById(pilotId).map(User::getUsername).orElse("A pilot");
        if (designer != null) {
            emailService.sendNewBid(new NewBidEmail(designer, mission, pilotName, amount, message));
        }
        return saved;
    }

    /**
     * The bids visible to the caller on a mission: the owning designer sees them
     * all; anyone else sees only their own (so the same endpoint feeds both the
     * designer's list and the pilot's "your bid" panel).
     */
    public List<Bid> listForMission(Long missionId, Long currentUserId) {
        Mission mission = getMissionOrThrow(missionId);
        if (currentUserId.equals(mission.getDesignerId())) {
            return bidRepository.findByMission_IdOrderByCreatedAtDesc(missionId);
        }
        return bidRepository.findByMission_IdAndPilot_Id(missionId, currentUserId)
                .map(List::of)
                .orElseGet(List::of);
    }

    /** Every bid the caller has placed, newest first. */
    public List<Bid> myBids(Long pilotId) {
        return bidRepository.findByPilot_IdOrderByCreatedAtDesc(pilotId);
    }

    /**
     * Withdraw (delete) the caller's pending bid. A bid that is not the
     * caller's own is reported as not found rather than forbidden, so bid ids
     * can't be probed (mirrors the mission visibility pattern).
     */
    public void withdraw(Long bidId, Long pilotId) {
        Bid bid = getBidOrThrow(bidId);
        if (!pilotId.equals(bid.getPilot().getId())) {
            throw new BidNotFoundException(bidId);
        }
        if (bid.getStatus() != BidStatus.PENDING) {
            throw new BidConflictException(
                    "Bid %d has already been decided and cannot be withdrawn".formatted(bidId));
        }
        bidRepository.delete(bid);
    }

    /**
     * Accept one bid: it becomes ACCEPTED, every other pending bid on the
     * mission is REJECTED, and the mission is AWARDED to the bid's pilot.
     * Only the mission's owner may award, and only while it is open.
     */
    @Transactional
    public Bid accept(Long bidId, Long designerId) {
        Bid bid = getBidOrThrow(bidId);
        // Fresh, not cached: this awards the mission and writes it back.
        Mission mission = getFreshMissionOrThrow(bid.getMission().getId());
        if (!designerId.equals(mission.getDesignerId())) {
            throw new MissionAccessDeniedException(mission.getId());
        }
        if (!BIDDABLE_STATUSES.contains(mission.getStatus())) {
            throw new BidConflictException(
                    "Mission %d has already been awarded".formatted(mission.getId()));
        }
        if (bid.getStatus() != BidStatus.PENDING) {
            throw new BidConflictException(
                    "Bid %d has already been decided".formatted(bidId));
        }

        bid.setStatus(BidStatus.ACCEPTED);
        bidRepository.save(bid);
        List<Bid> losers = bidRepository.findByMission_IdAndStatus(mission.getId(), BidStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(bid.getId()))
                .toList();
        losers.forEach(other -> {
            other.setStatus(BidStatus.REJECTED);
            bidRepository.save(other);
        });

        mission.setStatus(MissionStatus.AWARDED);
        mission.setAwardedPilot(bid.getPilot());
        missionDao.save(mission);

        notifyDecision(mission, bid, true);
        losers.forEach(loser -> notifyDecision(mission, loser, false));
        return bid;
    }

    /** In-app notification + best-effort email to a pilot whose bid was decided. */
    private void notifyDecision(Mission mission, Bid bid, boolean accepted) {
        if (accepted) {
            notificationService.create(NewNotification.bidAccepted(bid.getPilot().getId(), mission));
        } else {
            notificationService.create(NewNotification.bidRejected(bid.getPilot().getId(), mission));
        }
        userRepository.findById(bid.getPilot().getId())
                .ifPresent(pilot -> emailService.sendBidDecision(pilot, mission, bid.getAmount(), accepted));
    }

    /** Read-only lookup — may be served from cache, so never hand the result to save(). */
    private Mission getMissionOrThrow(Long missionId) {
        return missionDao.findById(missionId)
                .orElseThrow(() -> new MissionNotFoundException(missionId));
    }

    /** Lookup for a flow that is about to modify the mission — always a live database row. */
    private Mission getFreshMissionOrThrow(Long missionId) {
        return missionDao.findFresh(missionId)
                .orElseThrow(() -> new MissionNotFoundException(missionId));
    }

    private Bid getBidOrThrow(Long bidId) {
        return bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));
    }
}
