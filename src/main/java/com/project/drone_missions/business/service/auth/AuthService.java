package com.project.drone_missions.business.service.auth;

import com.project.drone_missions.business.exception.auth.EmailAlreadyExistsException;
import com.project.drone_missions.business.exception.auth.InvalidCredentialsException;
import com.project.drone_missions.business.exception.user.UserNotFoundException;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Account registration, credential verification and profile lookup. Operates on
 * entities and primitives only — DTO mapping and JWT issuance live in the web layer.
 */
@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Persists a new account. The incoming entity carries the raw password in its
     * {@code passwordHash} field; this method hashes it before saving.
     *
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    public User register(User user) {
        if (repository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException(user.getEmail());
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        return repository.save(user);
    }

    /**
     * Verifies credentials and returns the matching user.
     *
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     */
    public User authenticate(String email, String rawPassword) {
        User user = repository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    /**
     * @throws UserNotFoundException if no user has the given id
     */
    public User getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
