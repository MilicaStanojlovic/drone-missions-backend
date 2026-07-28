package com.project.drone_missions.business.service.mission;

import com.project.drone_missions.business.exception.mission.MissionAccessDeniedException;
import com.project.drone_missions.business.exception.mission.MissionConflictException;
import com.project.drone_missions.business.exception.mission.MissionNotFoundException;
import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.business.service.notification.NewNotification;
import com.project.drone_missions.business.service.notification.NotificationService;
import com.project.drone_missions.data.access.MissionDataAccess;
import com.project.drone_missions.data.access.OpenMissionQuery;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.BidStatus;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.BidRepository;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class MissionService {

    /**
     * The statuses that make a mission part of the open marketplace — work actually on
     * offer, visible to everyone. DRAFT is excluded because the designer is still planning
     * it. AWARDED and later belong to one chosen pilot, and no mission records who that is
     * yet, so they stay hidden from everyone rather than shown to all.
     */
    private static final Set<MissionStatus> OPEN_STATUSES =
            Set.of(MissionStatus.PUBLISHED, MissionStatus.BIDDING);

    private final MissionDataAccess repository; // issue1
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public Mission create(Mission mission) {
        return repository.save(mission);
    }

    /**
     * The open marketplace: every mission currently available for work, visible to all,
     * narrowed by the optional feed filters. Blank location/keyword and a null date are
     * treated as "not filtering". The date selects missions flyable on that day — i.e.
     * whose flight window overlaps it. Day bounds are computed in the server's local zone
     * so the filter matches the dates as they were entered and are displayed (the client
     * stores/shows flight windows in local time); a fixed UTC boundary would be off by the
     * timezone offset. Assumes the app runs in a single timezone.
     */
    public List<Mission> findOpen(String location, String keyword, LocalDate date) {
        String loc = normalize(location);
        String kw = normalize(keyword);
        ZoneId zone = ZoneId.systemDefault();
        Instant dayStart = date == null ? null : date.atStartOfDay(zone).toInstant();
        Instant dayEndExclusive = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();

        return repository.findOpen(
                new OpenMissionQuery(OPEN_STATUSES, loc, kw, dayStart, dayEndExclusive));
    }

    /**
     * Treats a null/blank filter value as "not provided" so the query's IS NULL guard skips
     * it, and lowercases the rest. Both filters match case-insensitively at the SQL layer, so
     * without this two requests differing only in case (e.g. "Novi Sad" vs. "novi sad") would
     * build unequal {@link OpenMissionQuery} records and land as separate entries in the list
     * cache despite returning identical results.
     */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    /** The missions the caller created and owns. */
    public List<Mission> findOwnedBy(Long currentUserId) {
        return repository.findByUserId(currentUserId);
    }

    /** The missions awarded to the calling pilot (their "jobs"). */
    public List<Mission> findAwardedTo(Long pilotId) {
        return repository.findByAwardedPilotId(pilotId);
    }

    /**
     * The awarded pilot starts the mission, moving it AWARDED → IN_PROGRESS. Only the
     * awarded pilot may start it, and only while it is still AWARDED. Starting is a
     * deliberate action — a mission never advances on its own.
     */
    public Mission start(Long id, Long pilotId) {
        Mission mission = getFreshOrThrow(id);
        if (!pilotId.equals(mission.getAwardedPilotId())) {
            throw new MissionAccessDeniedException(id);
        }
        if (mission.getStatus() != MissionStatus.AWARDED) {
            throw new MissionConflictException(
                    "Mission %d cannot be started from status %s".formatted(id, mission.getStatus()));
        }
        mission.setStatus(MissionStatus.IN_PROGRESS);
        return repository.save(mission);
    }

    /**
     * The winning pilot marks the mission finished, moving it to COMPLETED. Only the
     * awarded pilot may do this, and only once the mission is actually underway
     * (IN_PROGRESS) — it must be started first, and once completed it cannot be
     * completed again.
     */
    public Mission complete(Long id, Long pilotId) {
        Mission mission = getFreshOrThrow(id);
        if (!pilotId.equals(mission.getAwardedPilotId())) {
            throw new MissionAccessDeniedException(id);
        }
        if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new MissionConflictException(
                    "Mission %d cannot be completed from status %s".formatted(id, mission.getStatus()));
        }
        mission.setStatus(MissionStatus.COMPLETED);
        return repository.save(mission);
    }

    /**
     * The mission's creator cancels it, moving it to CANCELLED. Allowed from any status
     * that is not yet COMPLETED (and not already CANCELLED). Every outstanding bid is
     * rejected so no pilot is left expecting to win, and the awarded pilot — if one was
     * already chosen — is notified in-app and by (best-effort) email.
     */
    @Transactional
    public Mission cancel(Long id, Long designerId) {
        Mission mission = getFreshOrThrow(id);
        requireOwner(mission, designerId);
        if (mission.getStatus() == MissionStatus.COMPLETED
                || mission.getStatus() == MissionStatus.CANCELLED) {
            throw new MissionConflictException(
                    "Mission %d cannot be cancelled from status %s".formatted(id, mission.getStatus()));
        }
        mission.setStatus(MissionStatus.CANCELLED);
        repository.save(mission);

        bidRepository.findByMissionIdOrderByCreatedAtDesc(mission.getId()).forEach(bid -> {
            if (bid.getStatus() == BidStatus.PENDING || bid.getStatus() == BidStatus.ACCEPTED) {
                bid.setStatus(BidStatus.REJECTED);
                bidRepository.save(bid);
            }
        });

        Long pilotId = mission.getAwardedPilotId();
        if (pilotId != null) {
            notificationService.create(NewNotification.missionCancelled(pilotId, mission));
            userRepository.findById(pilotId)
                    .ifPresent(pilot -> emailService.sendMissionCancelled(pilot, mission));
        }
        return mission;
    }

    /**
     * @throws MissionNotFoundException if no such mission exists <em>or</em> the caller
     *         may not see it — a mission a caller cannot read must not be distinguishable
     *         from one that does not exist, or the 404 vs 403 itself would confirm a
     *         draft's existence.
     */
    public Mission findById(Long id, Long currentUserId) {
        Mission mission = getOrThrow(id);

        if (!isVisibleTo(mission, currentUserId)) {
            throw new MissionNotFoundException(id);
        }
        return mission;
    }

    public Mission update(Long id, Mission changes, Long currentUserId) {
        Mission mission = getFreshOrThrow(id);

        requireOwner(mission, currentUserId);
        mission.setName(changes.getName());
        mission.setDescription(changes.getDescription());
        mission.setStartTime(changes.getStartTime());
        mission.setEndTime(changes.getEndTime());
        mission.setLocation(changes.getLocation());
        mission.setBiddingDeadline(changes.getBiddingDeadline());
        mission.setWaypoints(changes.getWaypoints());
        mission.setGeofence(changes.getGeofence());
        // status is intentionally not modified on update — a mission's
        // lifecycle status is never changed by an edit.
        return repository.save(mission);
    }

    public void delete(Long id, Long currentUserId) {
        Mission mission = getFreshOrThrow(id);
        requireOwner(mission, currentUserId);
        repository.delete(mission);
    }

    /** Read-only lookup — may be served from cache, so never hand the result to save(). */
    private Mission getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MissionNotFoundException(id));
    }

    /** Lookup for a flow that is about to modify the mission — always a live database row. */
    private Mission getFreshOrThrow(Long id) {
        return repository.findFresh(id)
                .orElseThrow(() -> new MissionNotFoundException(id));
    }

    /** Visible to its owner, to the awarded pilot, or to anyone once it is open for work. */
    private boolean isVisibleTo(Mission mission, Long currentUserId) {
        return currentUserId.equals(mission.getUserId())
                || currentUserId.equals(mission.getAwardedPilotId())
                || OPEN_STATUSES.contains(mission.getStatus());
    }

    /** Only the mission's creator may modify or delete it. */
    private void requireOwner(Mission mission, Long currentUserId) {
        if (!currentUserId.equals(mission.getUserId())) {
            throw new MissionAccessDeniedException(mission.getId());
        }
    }
}
