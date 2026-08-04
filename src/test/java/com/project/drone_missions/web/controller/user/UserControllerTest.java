package com.project.drone_missions.web.controller.user;

import com.project.drone_missions.business.service.auth.AuthService;
import com.project.drone_missions.business.service.user.UserService;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.web.dto.user.NewAdminRequest;
import com.project.drone_missions.web.dto.auth.UserResponse;
import com.project.drone_missions.web.mapper.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthService authService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, authService, new UserMapper());
    }

    @Test
    void createAdminPassesThePrincipalAndReturns201() {
        User created = new User();
        created.setId(4L);
        created.setUsername("second-admin");
        created.setEmail("admin2@example.com");
        created.setRole(UserRole.ADMIN);
        when(authService.createAdmin("second-admin", "admin2@example.com", "pw-long-enough", 80L))
                .thenReturn(created);

        ResponseEntity<UserResponse> response = controller.createAdmin(
                new NewAdminRequest("second-admin", "admin2@example.com", "pw-long-enough"), 80L);

        verify(authService).createAdmin("second-admin", "admin2@example.com", "pw-long-enough", 80L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().username()).isEqualTo("second-admin");
        assertThat(response.getBody().role()).isEqualTo(UserRole.ADMIN);
    }
}
