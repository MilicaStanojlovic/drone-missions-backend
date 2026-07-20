package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.business.exception.auth.EmailAlreadyExistsException;
import com.project.drone_missions.business.exception.auth.InvalidCredentialsException;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import com.project.drone_missions.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Registration and authentication. Credential verification is delegated to Spring
 * Security's {@link AuthenticationManager} (backed by {@code CustomUserDetailsService}
 * + {@code PasswordEncoder}); this service just triggers it and mints the JWT with
 * Spring's {@link JwtEncoder}. User lookup lives in {@code UserService}.
 *
 * <p>Raw passwords are accepted as plain arguments rather than on an entity: a
 * {@code User} only ever holds the BCrypt hash, so the raw value must not travel on one.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    public User createUser(String username, String email, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     */
    public LoginResult login(String email, String rawPassword) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (AuthenticationException exception) {
            // Spring Security hides "user not found" as bad credentials; surface a single 401.
            throw new InvalidCredentialsException();
        }
        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();
        return new LoginResult(generateToken(user), user);
    }

    /** Mints an HS256 token whose subject is the user id and which carries a {@code role} claim. */
    private String generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusMillis(jwtExpirationMs))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
