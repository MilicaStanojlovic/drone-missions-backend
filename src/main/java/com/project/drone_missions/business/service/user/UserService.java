package com.project.drone_missions.business.service.user;

import com.project.drone_missions.business.exception.user.AdminCannotBeSuspendedException;
import com.project.drone_missions.business.exception.user.UserNotFoundException;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User account lookup. Holds no authentication/authorization logic — that lives in
 * AuthService, which also owns registration.
 */
@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final MissionDao missionDao;

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    /** Every account on the platform — the admin roster. */
    public List<User> findAll() {
        return repository.findAll();
    }

    /**
     * @throws UserNotFoundException if no user has the given id
     */
    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Admin: suspend the account. Idempotent. Mission feed lists are invalidated
     * because a suspended designer's missions leave the marketplace without any
     * mission row being written.
     */
    public User suspend(Long id) {
        User user = findById(id);
        if (user.getRole() == UserRole.ADMIN) {
            throw new AdminCannotBeSuspendedException(id);
        }
        if (!user.isSuspended()) {
            user.setSuspendedAt(Instant.now());
            repository.save(user);
            missionDao.invalidateLists();
        }
        return user;
    }

    /** Admin: lift a suspension. Idempotent; the counterpart of {@link #suspend}. */
    public User reactivate(Long id) {
        User user = findById(id);
        if (user.isSuspended()) {
            user.setSuspendedAt(null);
            repository.save(user);
            missionDao.invalidateLists();
        }
        return user;
    }
}
