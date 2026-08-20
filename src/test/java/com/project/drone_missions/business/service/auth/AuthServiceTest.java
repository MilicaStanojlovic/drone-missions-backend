package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.business.exception.auth.AdminRegistrationNotAllowedException;
import com.project.drone_missions.business.exception.auth.EmailAlreadyExistsException;
import com.project.drone_missions.business.exception.auth.InvalidCredentialsException;
import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.business.service.audit.NewAuditEntry;
import com.project.drone_missions.data.model.AuditAction;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import com.project.drone_missions.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Admin accounts are seeded by migration; the open register endpoint must never mint one. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService service;

    @Test
    void createUserRejectsAdminRole() {
        assertThatThrownBy(() -> service.createUser("eve", "eve@example.com", "pw", UserRole.ADMIN))
                .isInstanceOf(AdminRegistrationNotAllowedException.class);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void registrationRecordsTheNewUserAsTheActor() {
        when(passwordEncoder.encode("pw")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(3L);
            return u;
        });

        service.createUser("mira", "mira@example.com", "pw", UserRole.PILOT);

        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo(3L);
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.USER_REGISTERED);
        assertThat(captor.getValue().targetId()).isEqualTo(3L);
    }

    @Test
    void successfulLoginRecordsTheUser() {
        User user = new User();
        user.setId(3L);
        user.setUsername("mira");
        user.setRole(UserRole.PILOT);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(new UserPrincipal(user), null));
        when(jwtEncoder.encode(any())).thenReturn(
                Jwt.withTokenValue("token").header("alg", "HS256").claim("sub", "3").build());

        service.login("mira@example.com", "pw");

        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.USER_LOGGED_IN);
        assertThat(captor.getValue().actorId()).isEqualTo(3L);
    }

    @Test
    void createAdminMintsAnAdminAndRecordsTheCreatorAsActor() {
        when(passwordEncoder.encode("pw-long-enough")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(4L);
            return u;
        });

        User created = service.createAdmin("second-admin", "admin2@example.com", "pw-long-enough", 80L);

        assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(created.getPasswordHash()).isEqualTo("hash");
        ArgumentCaptor<NewAuditEntry> captor = ArgumentCaptor.forClass(NewAuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.ADMIN_CREATED);
        assertThat(captor.getValue().actorId()).isEqualTo(80L);
        assertThat(captor.getValue().targetId()).isEqualTo(4L);
    }

    @Test
    void createAdminRejectsADuplicateEmailWithoutSavingOrRecording() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin("x", "taken@example.com", "pw-long-enough", 80L))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void failedLoginRecordsNothing() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login("mira@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(auditService, never()).record(any());
    }
}
