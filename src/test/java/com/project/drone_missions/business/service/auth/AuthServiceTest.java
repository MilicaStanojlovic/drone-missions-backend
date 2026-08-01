package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.business.exception.auth.EmailAlreadyExistsException;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService#createUser} checks {@code existsByEmail} then calls {@code save} —
 * a classic check-then-act race. These tests pin that a concurrent duplicate registration,
 * which slips past the pre-check and hits the {@code users_email_unique} constraint on
 * save, still surfaces as {@link EmailAlreadyExistsException} (-> 409) rather than an
 * unhandled {@link DataIntegrityViolationException} (-> 500).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "pilot@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtEncoder);
        ReflectionTestUtils.setField(service, "jwtExpirationMs", 3_600_000L);
    }

    @Test
    void createUser_rejectsUpfrontWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.createUser("pilot", EMAIL, "password", UserRole.PILOT))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_translatesConcurrentDuplicateSaveIntoEmailAlreadyExists() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createUser("pilot", EMAIL, "password", UserRole.PILOT))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
