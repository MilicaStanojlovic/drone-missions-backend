package com.project.drone_missions.business.service.user;

import com.project.drone_missions.business.exception.user.UserNotFoundException;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * User account lookup. Holds no authentication/authorization logic — that lives in
 * AuthService, which also owns registration.
 */
@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository repository;

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    /**
     * @throws UserNotFoundException if no user has the given id
     */
    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
