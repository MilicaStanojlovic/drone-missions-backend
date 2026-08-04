package com.project.drone_missions.business.service.user;

import com.project.drone_missions.business.exception.user.AdminCannotBeSuspendedException;
import com.project.drone_missions.business.exception.user.UserNotFoundException;
import com.project.drone_missions.business.service.audit.AuditService;
import com.project.drone_missions.business.service.audit.NewAuditEntry;
import com.project.drone_missions.data.access.MissionDao;
import com.project.drone_missions.data.model.User;
import com.project.drone_missions.data.model.UserRole;
import com.project.drone_missions.data.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final MissionDao missionDao;
    private final AuditService auditService;

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    /** The admin listing; a null role means "everyone". */
    public Page<User> search(UserRole role, Pageable pageable) {
        return repository.search(role, pageable);
    }

    /**
     * @throws UserNotFoundException if no user has the given id
     */
    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Feed lists are invalidated because a suspended designer's missions leave
     * the marketplace without any mission row being written.
     */
    public User suspend(Long id, Long adminId) { // TODO project root xml check style, pre commit hooks.. identacija npr, parametri komentari
        User user = findById(id);
        if (user.getRole() == UserRole.ADMIN) {
            throw new AdminCannotBeSuspendedException(id);
        }
        if (!user.isSuspended()) {
            user.setSuspended(true);
            repository.save(user);
            missionDao.invalidateLists();
            auditService.record(NewAuditEntry.userSuspended(adminId, user));
        }
        return user;
    }

    public User reactivate(Long id, Long adminId) {
        User user = findById(id);
        if (user.isSuspended()) {
            user.setSuspended(false);
            repository.save(user);
            missionDao.invalidateLists();
            auditService.record(NewAuditEntry.userReactivated(adminId, user));
        }
        return user;
    }
}
