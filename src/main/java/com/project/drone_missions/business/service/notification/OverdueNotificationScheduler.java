package com.project.drone_missions.business.service.notification;

import com.project.drone_missions.business.service.mail.EmailService;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.MissionStatus;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Hourly sweep that nudges pilots about won missions whose flight window has
 * ended. For each newly-overdue mission it creates a MISSION_OVERDUE
 * notification and sends the "has your flight ended?" email — once per mission,
 * guarded by {@link NotificationService#overdueExists}.
 */
@Component
@AllArgsConstructor
@Slf4j
public class OverdueNotificationScheduler {

    private static final Set<MissionStatus> ACTIVE_AWARDED =
            Set.of(MissionStatus.AWARDED, MissionStatus.IN_PROGRESS);

    private final MissionDao missionDao;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /** Runs once a day at 09:00. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Belgrade")
    public void notifyOverdueMissions() {
        ZoneId zone = ZoneId.of("Europe/Belgrade");
        Instant cutoff = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        List<Mission> overdue = missionDao.findOverdue(ACTIVE_AWARDED, cutoff);

        int notified = 0;
        for (Mission mission : overdue) {
            Long pilotId = mission.getAwardedPilotId();
            if (notificationService.overdueExists(pilotId, mission.getId())) {
                continue;
            }
            notificationService.create(NewNotification.missionOverdue(pilotId, mission));
            userRepository.findById(pilotId)
                    .ifPresent(pilot -> emailService.sendMissionOverdue(pilot, mission));
            notified++;
        }
        if (notified > 0) {
            log.info("Overdue sweep: notified {} pilot(s) of finished-flight checks", notified);
        }
    }
}
