package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.Bid;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.Rating;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewAuditEntryTest {

    private static Mission mission() {
        Mission m = new Mission();
        m.setId(4L);
        m.setName("Orchard survey");
        return m;
    }

    @Test
    void designerFactoriesPairRoleActionAndNameSnapshot() {
        NewAuditEntry entry = NewAuditEntry.missionCreated(7L, mission());

        assertThat(entry.actorId()).isEqualTo(7L);
        assertThat(entry.actorRole()).isEqualTo(UserRole.DESIGNER);
        assertThat(entry.action()).isEqualTo(AuditAction.MISSION_CREATED);
        assertThat(entry.targetType()).isEqualTo(AuditTargetType.MISSION);
        assertThat(entry.targetId()).isEqualTo(4L);
        assertThat(entry.details()).isEqualTo("\"Orchard survey\"");
    }

    @Test
    void pilotFactoriesUseThePilotRole() {
        assertThat(NewAuditEntry.missionStarted(5L, mission()).actorRole())
                .isEqualTo(UserRole.PILOT);
        assertThat(NewAuditEntry.missionCompleted(5L, mission()).action())
                .isEqualTo(AuditAction.MISSION_COMPLETED);
    }

    @Test
    void moderationFactoriesUseTheAdminRole() {
        assertThat(NewAuditEntry.missionHidden(9L, mission()).actorRole()).isEqualTo(UserRole.ADMIN);
        assertThat(NewAuditEntry.missionUnhidden(9L, mission()).action())
                .isEqualTo(AuditAction.MISSION_UNHIDDEN);
        assertThat(NewAuditEntry.missionRemoved(9L, mission()).action())
                .isEqualTo(AuditAction.MISSION_REMOVED);
        assertThat(NewAuditEntry.missionRestored(9L, mission()).action())
                .isEqualTo(AuditAction.MISSION_RESTORED);
    }

    @Test
    void userFactoriesTargetTheUserAndSnapshotTheUsername() {
        User target = new User();
        target.setId(3L);
        target.setUsername("pilot-mira");

        NewAuditEntry entry = NewAuditEntry.userSuspended(9L, target);

        assertThat(entry.actorRole()).isEqualTo(UserRole.ADMIN);
        assertThat(entry.action()).isEqualTo(AuditAction.USER_SUSPENDED);
        assertThat(entry.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(entry.targetId()).isEqualTo(3L);
        assertThat(entry.details()).isEqualTo("\"pilot-mira\"");
        assertThat(NewAuditEntry.userReactivated(9L, target).action())
                .isEqualTo(AuditAction.USER_REACTIVATED);
    }

    private static Bid bid() {
        Bid b = new Bid();
        b.setId(8L);
        b.setMission(mission());
        b.setAmount(BigDecimal.TEN);
        return b;
    }

    @Test
    void bidFactoriesSnapshotAmountAndMissionName() {
        NewAuditEntry placed = NewAuditEntry.bidPlaced(5L, bid(), false);
        assertThat(placed.actorRole()).isEqualTo(UserRole.PILOT);
        assertThat(placed.targetType()).isEqualTo(AuditTargetType.BID);
        assertThat(placed.targetId()).isEqualTo(8L);
        assertThat(placed.details()).isEqualTo("10 on \"Orchard survey\"");

        assertThat(NewAuditEntry.bidPlaced(5L, bid(), true).details()).endsWith("(updated)");
        assertThat(NewAuditEntry.bidWithdrawn(5L, bid()).action())
                .isEqualTo(AuditAction.BID_WITHDRAWN);
        assertThat(NewAuditEntry.bidAccepted(7L, bid()).actorRole())
                .isEqualTo(UserRole.DESIGNER);
    }

    @Test
    void selfActionsCarryTheUsersOwnRole() {
        User user = new User();
        user.setId(3L);
        user.setUsername("pilot-mira");
        user.setRole(UserRole.PILOT);

        NewAuditEntry registered = NewAuditEntry.userRegistered(user);
        assertThat(registered.actorId()).isEqualTo(3L);
        assertThat(registered.actorRole()).isEqualTo(UserRole.PILOT);
        assertThat(registered.targetId()).isEqualTo(3L);
        assertThat(NewAuditEntry.userLoggedIn(user).action())
                .isEqualTo(AuditAction.USER_LOGGED_IN);
    }

    @Test
    void ratingRoleIsDerivedFromWhichParticipantRated() {
        Mission m = mission();
        User designer = new User();
        designer.setId(7L);
        m.setDesigner(designer);
        Rating rating = new Rating();
        rating.setId(11L);
        rating.setScore((short) 4);

        assertThat(NewAuditEntry.ratingCreated(7L, m, rating).actorRole())
                .isEqualTo(UserRole.DESIGNER);
        assertThat(NewAuditEntry.ratingCreated(5L, m, rating).actorRole())
                .isEqualTo(UserRole.PILOT);
        assertThat(NewAuditEntry.ratingCreated(5L, m, rating).details())
                .isEqualTo("4/5 on \"Orchard survey\"");
    }

    @Test
    void actorIdIsMandatory() {
        assertThatThrownBy(() -> NewAuditEntry.missionCreated(null, mission()))
                .isInstanceOf(NullPointerException.class);
    }
}
