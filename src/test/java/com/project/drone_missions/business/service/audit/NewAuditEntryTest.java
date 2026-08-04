package com.project.drone_missions.business.service.audit;

import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.AuditTargetType;
import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import org.junit.jupiter.api.Test;

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

    @Test
    void actorIdIsMandatory() {
        assertThatThrownBy(() -> NewAuditEntry.missionCreated(null, mission()))
                .isInstanceOf(NullPointerException.class);
    }
}
