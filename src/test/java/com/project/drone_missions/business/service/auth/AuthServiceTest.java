package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.business.exception.auth.AdminRegistrationNotAllowedException;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @InjectMocks
    private AuthService service;

    @Test
    void createUserRejectsAdminRole() {
        assertThatThrownBy(() -> service.createUser("eve", "eve@example.com", "pw", UserRole.ADMIN))
                .isInstanceOf(AdminRegistrationNotAllowedException.class);
        verifyNoInteractions(userRepository);
    }
}
